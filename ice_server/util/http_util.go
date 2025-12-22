package util

import (
	eh "github.com/labstack/echo/v4"
	"net/http"
)

var HttpUtil = &httpUtil{}

type (
	httpUtil struct{}
	Msg      struct {
		Code int         `json:"code"`
		Err  string      `json:"err"`
		Data interface{} `json:"data,omitempty"`
	}
)

func (h *httpUtil) Error(c eh.Context, err error) error {
	return h.ErrorCode(c, 1, err)
}

func (h *httpUtil) ErrorCode(c eh.Context, code int, err error) error {
	return h.ErrorCodeMsg(c, code, err.Error())
}

func (h *httpUtil) ErrorMsg(c eh.Context, msg string) error {
	return h.ErrorCodeMsg(c, 1, msg)
}

func (h *httpUtil) ErrorCodeMsg(c eh.Context, code int, msg string) error {
	return c.JSON(http.StatusOK, h.ErrorCodeMsgStruct(code, msg))
}

func (h *httpUtil) ErrorMsgStruct(err string) *Msg {
	return h.ErrorCodeMsgStruct(1, err)
}

func (h *httpUtil) ErrorCodeMsgStruct(code int, err string) *Msg {
	return &Msg{
		Code: code,
		Err:  err,
	}
}

func (h *httpUtil) OK(c eh.Context, data interface{}) error {
	return c.JSON(http.StatusOK, h.OKMsgStruct(data))
}

func (h *httpUtil) OKMsgStruct(data interface{}) *Msg {
	return &Msg{
		Code: 200,
		Data: data,
	}
}
