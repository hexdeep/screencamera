package handler

import (
	"github.com/gorilla/websocket"
	eh "github.com/labstack/echo/v4"
	"ice_server/acceptor"
	"ice_server/constants"
	"ice_server/instance"
	"ice_server/util"
	"net/http"
)

var upGrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true // 注意：生产环境中需要做安全检查
	},
}

type websocketHandler struct {
}

func (*websocketHandler) Ws(c eh.Context) error {
	var deviceId = c.QueryParam("device_id")
	if deviceId == "" {
		return util.HttpUtil.ErrorMsg(c, "设备id不能为空")
	}

	// 升级 HTTP 连接为 WebSocket
	conn, err := upGrader.Upgrade(c.Response(), c.Request(), nil)
	if err != nil {
		return util.HttpUtil.Error(c, err)
	}

	instance.App.GetComponent(constants.AcceptorDesc).(*acceptor.Acceptor).AddClient(acceptor.NewClient(deviceId, conn))
	return util.HttpUtil.OK(c, "ok")
}
