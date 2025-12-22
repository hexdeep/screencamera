package acceptor

import (
	"fmt"
	"github.com/rs/zerolog/log"
	"ice_server/constants"
	"sync"
	"time"
)

// Acceptor 管理客户端
type Acceptor struct {
	clients map[string]*Client
	lock    sync.Mutex
}

func NewAcceptor() *Acceptor {
	return &Acceptor{
		clients: make(map[string]*Client),
	}
}

func (a *Acceptor) Init() error {
	go func() {
		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		heartbeatTimeout := int64(24 * 1000) // 24秒

		for range ticker.C {
			now := time.Now().UnixMilli()
			var removeClients []*Client

			// 收集超时客户端，锁内访问 map
			a.lock.Lock()
			for _, cli := range a.clients {
				if now-cli.LastAt > heartbeatTimeout {
					removeClients = append(removeClients, cli)
					delete(a.clients, cli.DeviceId) // 先从 map 删除
				}
			}
			a.lock.Unlock()

			for _, cli := range removeClients {
				fmt.Println("客户端超时移除:", cli.DeviceId)
				cli.Conn.Close()
			}
		}
	}()

	return nil
}

func (a *Acceptor) GetClient(deviceId string) *Client {
	a.lock.Lock()
	defer a.lock.Unlock()

	return a.clients[deviceId]
}

func (a *Acceptor) AddClient(c *Client) {
	log.Info().Msgf("add client:%v", c.DeviceId)
	a.lock.Lock()
	if oldCli, ok := a.clients[c.DeviceId]; ok {
		oldCli.Conn.Close()
		delete(a.clients, c.DeviceId)
	}
	a.clients[c.DeviceId] = c
	a.lock.Unlock()

	c.Conn.SetCloseHandler(func(code int, text string) error {
		a.lock.Lock()
		defer a.lock.Unlock()
		if _, ok := a.clients[c.DeviceId]; ok {
			delete(a.clients, c.DeviceId)
			fmt.Println("客户端关闭移除:", c.DeviceId)
		}
		return nil
	})

	go c.Init()
}

func (*Acceptor) Stop() error {
	return nil
}

func (*Acceptor) Desc() string {
	return constants.AcceptorDesc
}
