package app

import (
	"github.com/rs/zerolog/log"
	"ice_server/component"
	"os"
	"os/signal"
	"syscall"
)

type App struct {
	components []component.Component
	dieChan    chan bool
}

func NewApp(components ...component.Component) *App {
	return &App{
		components: components,
		dieChan:    make(chan bool, 1),
	}
}

func (p *App) Start() (err error) {
	for _, cmp := range p.components {
		err = cmp.Init()
		if err != nil {
			log.Error().Msgf("App Init %s failed:%v", cmp.Desc(), err)
			return err
		}
		log.Info().Msgf("App Init %s success...", cmp.Desc())
	}

	sg := make(chan os.Signal)
	signal.Notify(sg, syscall.SIGINT, syscall.SIGQUIT, syscall.SIGKILL, syscall.SIGTERM)
	select {
	case <-p.dieChan:
		log.Error().Msgf("The instance will down in a few time")
	case s := <-sg:
		p.Stop()
		log.Error().Msgf("got signal:%v,shutdown...", s)
		close(p.dieChan)
	}

	return nil
}

func (p *App) Stop() {
	var err error
	for _, cmp := range p.components {
		err = cmp.Stop()
		if err != nil {
			log.Error().Msgf("App Stop %s failed:%v", cmp, err)
		}
		log.Info().Msgf("App Stop %s success...", cmp.Desc())
	}
	return
}

func (p *App) GetComponent(desc string) component.Component {
	for _, v := range p.components {
		if v.Desc() == desc {
			return v
		}
	}
	return nil
}
