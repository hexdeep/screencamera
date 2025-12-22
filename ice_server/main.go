package main

import (
	"github.com/rs/zerolog/log"
	"ice_server/acceptor"
	"ice_server/app"
	"ice_server/constants"
	"ice_server/echo"
	"ice_server/http/handler"
	"ice_server/instance"
	lg "ice_server/log"
	"ice_server/util"
	"path/filepath"
)

func main() {
	err := lg.NewLog(filepath.Join(util.FileUtil.GetCurrentExecDirectory(), "log", "log.txt")).Init()
	if err != nil {
		log.Error().Msgf("Init log failed:%v", err)
		return
	}
	log.Info().Msgf("App Init log success...")

	instance.App = app.NewApp(
		acceptor.NewAcceptor(),
		echo.NewEcho(constants.EchoPort, handler.PostHandlers, handler.GetHandlers),
	)
	err = instance.App.Start()
	if err != nil {
		log.Error().Msgf("App Start failed:%v", err)
	}
}
