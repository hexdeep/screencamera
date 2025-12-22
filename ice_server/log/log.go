package log

import (
	"github.com/rs/zerolog"
	zlog "github.com/rs/zerolog/log"
	"gopkg.in/natefinch/lumberjack.v2"
	"ice_server/constants"
	"os"
	"path/filepath"
)

type log struct {
	logPath string
}

func NewLog(logPath string) *log {
	return &log{
		logPath: logPath,
	}
}

func (l *log) Init() error {
	err := os.MkdirAll(filepath.Dir(l.logPath), 0755)
	if err != nil {
		zlog.Error().Msgf("Failed to create log parent dir:%v", err)
		return err
	}

	logOutput := &lumberjack.Logger{
		Filename:   l.logPath, // 日志文件名
		MaxSize:    1,         // 每个日志文件的最大大小（以MB为单位）
		MaxBackups: 1,         // 保留旧日志文件的最大个数
		MaxAge:     28,        // 保留旧日志文件的最大天数
		Compress:   false,     // 是否压缩旧日志文件
	}

	multi := zerolog.MultiLevelWriter(
		zerolog.ConsoleWriter{Out: logOutput, TimeFormat: "2006-01-02 15:04:05.000", NoColor: true},
		zerolog.ConsoleWriter{
			Out:        os.Stdout,
			TimeFormat: "2006-01-02 15:04:05.000",
			NoColor:    false,
		},
	)
	zlog.Logger = zerolog.New(multi).With().Timestamp().Logger()
	zlog.Level(zerolog.InfoLevel)
	return nil
}

func (*log) Stop() error {
	return nil
}

func (*log) Desc() string {
	return constants.LogCmpDesc
}
