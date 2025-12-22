package client

import (
	"fmt"
	"github.com/gorilla/websocket"
	"sync"
	"time"
)

type WSManager struct {
	url        string
	deviceId   string
	conn       *websocket.Conn
	lock       sync.Mutex
	running    bool
	lastPongAt int64
}

// 全局单例
var manager *WSManager
var once sync.Once

// GetManager 单例
func GetManager(url, deviceId string) *WSManager {
	once.Do(func() {
		manager = &WSManager{
			url:      url,
			deviceId: deviceId,
		}
	})
	return manager
}

// Start 启动客户端
func (w *WSManager) Start() {
	w.lock.Lock()
	if w.running {
		w.lock.Unlock()
		return
	}
	w.running = true
	w.lock.Unlock()

	go w.connectLoop()
}

// connectLoop 自动连接+重连
func (w *WSManager) connectLoop() {
	for w.running {
		err := w.connect()
		if err != nil {
			fmt.Println("连接失败，3秒后重试:", err)
			time.Sleep(3 * time.Second)
			continue
		}

		w.readLoop() // 阻塞直到连接关闭
	}
}

// connect 建立 WebSocket 连接
func (w *WSManager) connect() error {
	w.lock.Lock()
	defer w.lock.Unlock()

	dialer := websocket.DefaultDialer
	url := fmt.Sprintf("%s?device_id=%s", w.url, w.deviceId)
	conn, _, err := dialer.Dial(url, nil)
	if err != nil {
		return err
	}

	w.conn = conn
	w.lastPongAt = time.Now().UnixMilli()

	// 设置 pong handler
	w.conn.SetPongHandler(func(appData string) error {
		w.lastPongAt = time.Now().UnixMilli()
		return nil
	})

	// 启动心跳
	go w.pingLoop()
	// 启动健康检测
	go w.healthCheckLoop()

	fmt.Println("WebSocket 已连接")
	return nil
}

// pingLoop 定时发送 ping
func (w *WSManager) pingLoop() {
	ticker := time.NewTicker(8 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		w.lock.Lock()
		if w.conn != nil {
			err := w.conn.WriteMessage(websocket.PingMessage, []byte("ping"))
			if err != nil {
				fmt.Println("发送 Ping 错误:", err)
			}
		}
		w.lock.Unlock()
	}
}

// healthCheckLoop 检测 lastPong
func (w *WSManager) healthCheckLoop() {
	ticker := time.NewTicker(3 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		w.lock.Lock()
		if w.conn != nil {
			if time.Now().UnixMilli()-w.lastPongAt > 16000 { // 16秒未收到pong
				fmt.Println("检测到连接失活，关闭重连")
				w.conn.Close()
				w.conn = nil
				w.lock.Unlock()
				return
			}
		}
		w.lock.Unlock()
	}
}

// readLoop 读取服务器消息
func (w *WSManager) readLoop() {
	for {
		w.lock.Lock()
		conn := w.conn
		w.lock.Unlock()

		if conn == nil {
			return
		}

		mt, msg, err := conn.ReadMessage()
		if err != nil {
			fmt.Println("ReadMessage 错误:", err)
			w.lock.Lock()
			w.conn.Close()
			w.conn = nil
			w.lock.Unlock()
			return
		}

		if mt == websocket.TextMessage {
			fmt.Println("收到消息:", string(msg))
		}
	}
}

// Send 发送消息（方案2：未连接返回错误）
func (w *WSManager) Send(msg string) error {
	w.lock.Lock()
	defer w.lock.Unlock()

	if w.conn == nil {
		return fmt.Errorf("WebSocket 未连接")
	}

	return w.conn.WriteMessage(websocket.TextMessage, []byte(msg))
}

// Stop 停止客户端
func (w *WSManager) Stop() {
	w.lock.Lock()
	defer w.lock.Unlock()
	w.running = false
	if w.conn != nil {
		w.conn.Close()
		w.conn = nil
	}
}
