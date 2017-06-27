package com.xw.appupdatedemo;

/**
 * 事件监听回调
 * Created by Administrator on 2017/6/27 0027.
 */

public interface UpDateDownloadListener {

    /**
     * 下载请求开始回调
     */
    public void onStarted();

    /**
     * 进度更新回调
     * @param progress
     * @param url
     */
    public void onProgressChanged(int progress,String url);

    /**
     * 下载完成回调
     * @param completeSize
     * @param url
     */
    public void onFinished(int completeSize,String url);

    /**
     * 下载失败回调
     */
    public void onFailure();

}
