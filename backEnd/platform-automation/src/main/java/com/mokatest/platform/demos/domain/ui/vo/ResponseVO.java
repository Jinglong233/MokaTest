package com.mokatest.platform.demos.domain.ui.vo;

import com.mokatest.platform.demos.domain.ui.uiEnum.response.ResponseCode;
import lombok.Data;

/**
 * @Author JingLong
 * @Description 接口返回对象
 * @Date 2025/8/1 20:04
 **/
@Data
public class ResponseVO {

    private int code;
    private String msg;
    private Object data;

    public ResponseVO() {
    }

    public ResponseVO(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ResponseVO success() {
        return new ResponseVO(ResponseCode.SUCCESS.code(), ResponseCode.SUCCESS.message());
    }

    public static ResponseVO success(Object data) {
        ResponseVO responseVO = new ResponseVO();
        responseVO.setCode(ResponseCode.SUCCESS.code());
        responseVO.setMsg(ResponseCode.SUCCESS.message());
        responseVO.setData(data);
        return responseVO;
    }


    public static ResponseVO failure(ResponseCode responseCode) {
        ResponseVO responseVO = new ResponseVO();
        responseVO.setCode(responseCode.code());
        responseVO.setMsg(responseCode.message());
        return responseVO;
    }


    public static ResponseVO failure(ResponseCode responseCode, Object data) {
        ResponseVO responseVO = new ResponseVO();
        responseVO.setCode(responseCode.code());
        responseVO.setMsg(responseCode.message());
        responseVO.setData(data);
        return responseVO;
    }

    public static ResponseVO failure(Integer responseCode, String message) {
        ResponseVO responseVO = new ResponseVO();
        responseVO.setCode(responseCode);
        responseVO.setMsg(message);
        return responseVO;
    }

}
