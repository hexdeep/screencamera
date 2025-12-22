package echo

import (
	"fmt"
	eh "github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
	"github.com/rs/zerolog/log"
	"ice_server/constants"
	"ice_server/util"
	"time"
)

type Echo struct {
	echo         *eh.Echo
	port         int
	middlewares  []func(next eh.HandlerFunc) eh.HandlerFunc
	postHandlers map[string]func(c eh.Context) error
	getHandlers  map[string]func(c eh.Context) error
}

func NewEcho(port int, postHandlers map[string]func(c eh.Context) error, getHandlers map[string]func(c eh.Context) error, middlewares ...func(next eh.HandlerFunc) eh.HandlerFunc) *Echo {
	return &Echo{
		echo:         eh.New(),
		port:         port,
		middlewares:  middlewares,
		postHandlers: postHandlers,
		getHandlers:  getHandlers,
	}
}

func (e *Echo) Init() error {
	// 使用 CORS 中间件
	e.echo.Use(middleware.CORSWithConfig(middleware.CORSConfig{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{eh.GET, eh.POST, eh.PUT, eh.DELETE},
		AllowHeaders:     []string{"Origin", "Content-Type", "Accept", "Authorization"},
		AllowCredentials: true,
	}))
	for _, v := range e.middlewares {
		e.echo.Use(v)
	}

	for k, v := range e.postHandlers {
		e.echo.POST(k, v)
	}

	for k, v := range e.getHandlers {
		e.echo.GET(k, v)
	}

	e.echo.HTTPErrorHandler = func(err error, c eh.Context) {
		util.HttpUtil.Error(c, err)
	}

	go func() {
		for i := 0; i < 10; i++ {
			err := e.echo.Start(fmt.Sprintf(":%d", e.port))
			if err != nil {
				log.Error().Msgf("Echo Init failed:%v", err)
				time.Sleep(2 * time.Second)
				continue
			}
			log.Info().Msgf("echo init success..., current port:%d", e.port)
			return
		}
	}()

	return nil
}

func (e *Echo) Stop() error {
	return e.Stop()
}

func (*Echo) Desc() string {
	return constants.EchoCmpDesc
}
