package util

import (
	"os"
	"path/filepath"
	"strings"
)

var FileUtil = &fileUtil{}

type fileUtil struct{}

func (*fileUtil) GetCurrentExecDirectory() string {
	dir, err := filepath.Abs(filepath.Dir(os.Args[0]))
	if err != nil {
		return ""
	}
	return strings.Replace(dir, "\\", "/", -1)
}
