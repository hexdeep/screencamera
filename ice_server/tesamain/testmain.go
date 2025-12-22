package main

import (
	client "ice_server/manager"
	"time"
)

func main() {
	manager := client.GetManager("ws://192.168.31.166:8880/ws", "device123")
	manager.Start()

	// 等待连接建立
	time.Sleep(2 * time.Second)

	err := manager.Send("hello server")
	if err != nil {
		println("发送失败:", err.Error())
	}

	// 模拟运行
	select {}
}
