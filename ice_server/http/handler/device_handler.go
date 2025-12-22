package handler

import (
	eh "github.com/labstack/echo/v4"
	"ice_server/acceptor"
	"ice_server/constants"
	"ice_server/instance"
	"ice_server/util"
)

type deviceHandler struct{}

func (*deviceHandler) Online(c eh.Context) error {
	id := c.QueryParam("id")
	if id == "" {
		return util.HttpUtil.ErrorMsg(c, "设备唯一秘钥不能为空")
	}

	cli := instance.App.GetComponent(constants.AcceptorDesc).(*acceptor.Acceptor).GetClient(id)
	if cli == nil {
		return util.HttpUtil.ErrorMsg(c, "设备不在线")
	}

	return util.HttpUtil.OK(c, "ok")
}
