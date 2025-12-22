package acceptor

import (
	"encoding/json"
	"github.com/gorilla/websocket"
	"github.com/rs/zerolog/log"
	"ice_server/constants"
	"ice_server/instance"
	"sync"
	"time"
)

type (
	Client struct {
		DeviceId string
		Conn     *websocket.Conn
		LastAt   int64

		running bool
		lock    sync.Mutex
	}

	WebrtcSingleMessage struct {
		FromDeviceId string `json:"from_device_id"`
		ToDeviceId   string `json:"to_device_id"`
		MessageType  string `json:"message_type"`
		Content      string `json:"content"`
	}
)

func NewClient(deviceId string, conn *websocket.Conn) *Client {
	return &Client{
		DeviceId: deviceId,
		Conn:     conn,
		LastAt:   time.Now().UnixMilli(),
		running:  true,
	}
}

func (c *Client) Init() {
	c.Conn.SetPingHandler(func(appData string) error {
		c.updateLastAt()
		go c.safeWrite(websocket.PongMessage, nil)
		return nil
	})

	c.Conn.SetPongHandler(func(appData string) error {
		c.updateLastAt()
		return nil
	})

	for c.running {
		mt, msg, err := c.Conn.ReadMessage()
		if err != nil {
			log.Error().Msgf("client[%s] ReadMessage failed: %v", c.DeviceId, err)
			break
		}

		if mt == websocket.CloseMessage {
			log.Info().Msgf("client[%s] received close message", c.DeviceId)
			break
		}

		if mt == websocket.TextMessage {
			var message WebrtcSingleMessage
			err = json.Unmarshal(msg, &message)
			if err != nil {
				log.Error().Msgf("client[%s] Unmarshal message %s failed: %v", c.DeviceId, string(msg), err)
				break
			}

			cli := instance.App.GetComponent(constants.AcceptorDesc).(*Acceptor).GetClient(message.ToDeviceId)
			//log.Info().Msgf("收到消息：messageType[%s] fromDeviceid[%s],toDeviceid[%s]", message.MessageType, message.FromDeviceId, message.ToDeviceId)
			if cli != nil {
				err = cli.safeWrite(websocket.TextMessage, msg)
				if err != nil {
					log.Error().Msgf("client[%s] safeWrite message %s failed: %v", cli.DeviceId, string(msg), err)
				}
			}
		}
	}

	c.Conn.Close()
	c.running = false
	c.Conn.CloseHandler()
	log.Info().Msgf("client[%s] exited", c.DeviceId)
}

func (c *Client) safeWrite(mt int, msg []byte) error {
	c.lock.Lock()
	defer c.lock.Unlock()
	return c.Conn.WriteMessage(mt, msg)
}

func (c *Client) updateLastAt() {
	c.LastAt = time.Now().UnixMilli()
}

func (c *Client) Close() {
	c.running = false
	c.Conn.Close()
}
