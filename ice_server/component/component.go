package component

type Component interface {
	Init() error
	Stop() error
	Desc() string
}
