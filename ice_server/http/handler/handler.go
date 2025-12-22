package handler

import (
	eh "github.com/labstack/echo/v4"
)

var (
	wsHandler = new(websocketHandler)
	device    = new(deviceHandler)

	GetHandlers = map[string]func(c eh.Context) error{
		"/ws":            wsHandler.Ws,
		"/device_online": device.Online,
	}
	PostHandlers = map[string]func(c eh.Context) error{}
)
