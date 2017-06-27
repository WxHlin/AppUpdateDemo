package com.xw.appupdatedemo;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

/**
 * 负责文件的下载和线程间的通信
 * Created by Administrator on 2017/6/27 0027.
 */

public class UpdateDownloadRequest implements Runnable {

    private String urlPath;//下载路径
    private String filePath;//文件的保存路径
    private UpDateDownloadListener downloadListener;//事件回调
    private boolean isDownload=false;//下载的标志
    private long currentLength;//文件长度

    private DownloadResponseHandler downloadHandler;

    public UpdateDownloadRequest(String url,String filePath,UpDateDownloadListener downloadListener){
        this.urlPath=url;
        this.filePath=filePath;
        this.downloadListener=downloadListener;
        this.isDownload=true;
        downloadHandler=new DownloadResponseHandler();
    }

    private String getTwoPointFloatStr(float value){
        DecimalFormat df = new DecimalFormat("0.00000000000");
        return df.format(value);

    }

    //真正去建立连接的方法
    private void makeRequest() throws IOException,InterruptedIOException {
        if (Thread.currentThread().isInterrupted()){
            URL url=new URL(urlPath);
            HttpURLConnection connection= (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setRequestProperty("Connection","Keep-Alive");
            connection.connect();//阻塞我们当前的线程
            currentLength=connection.getContentLength();
            if (Thread.currentThread().isInterrupted()){
                //真正的完成文件的下载
                downloadHandler.sendResponseMessage(connection.getInputStream());
            }
        }
    }

    @Override
    public void run() {
        try {
            makeRequest();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 下载过程中的所有可能出现的异常
     */
    public enum FailureCode{
        UnknownHost, Socket, SocketTimeout, connectionTimeout,IO, HttpResponse,
        Json, Interrupted

    }

    /**
     * 用来真正的去下载文件，并发回消息和回调的借口
     */
    public class DownloadResponseHandler{

        protected static final int SUCCESS_MESSAGE = 0;
        protected static final int FAILURE_MESSAGE = 1;
        protected static final int START_MESSAGE = 2;
        protected static final int FINISH_MESSAGE = 3;
        protected static final int NETWORK_OFF = 4;
        private static final int PROGRESS_CHANGED = 5;

        private int completeSize = 0;
        private int progress = 0;

        private Handler handler;

        public DownloadResponseHandler(){

            handler = new Handler(Looper.getMainLooper()) {

                @Override
                public void handleMessage(Message msg) {
                    handleSelfMessage(msg);
                }
            };

        }

        protected void sendFinishMessage(){
            sendMessage(obtainMessage(FINISH_MESSAGE, null));
        }

        private void sendProgressChangedMessage(int progress){
            sendMessage(obtainMessage(PROGRESS_CHANGED, new Object[]{progress}));

        }

        protected void sendFailureMessage(FailureCode failureCode){
            sendMessage(obtainMessage(FAILURE_MESSAGE, new Object[]{failureCode}));

        }

        protected void sendMessage(Message msg){
            if(handler!=null){
                handler.sendMessage(msg);
            }else{
                handleSelfMessage(msg);
            }

        }

        protected Message obtainMessage(int responseMessge, Object response){
            Message msg = null;
            if(handler!=null){
                msg = handler.obtainMessage(responseMessge, response);
            }else{
                msg = Message.obtain();
                msg.what = responseMessge;
                msg.obj = response;
            }
            return msg;

        }

        protected void handleSelfMessage(Message msg){

            Object[] response;
            switch (msg.what){
                case FAILURE_MESSAGE:
                    response = (Object[]) msg.obj;
                    sendFailureMessage((FailureCode) response[0]);
                    break;
                case PROGRESS_CHANGED:
                    response = (Object[]) msg.obj;
                    handleProgressChangedMessage(((Integer)response[0]).intValue());
                    break;
                case FINISH_MESSAGE:
                    onFinish();
                    break;
            }
        }

        protected void handleProgressChangedMessage(int progress){
            downloadListener.onProgressChanged(progress, urlPath);
        }

        //外部接口的回调
        protected void onFinish(){
            downloadListener.onFinished(completeSize, "");

        }

        private void handleFailureMessage(FailureCode failureCode){
            onFailure(failureCode);

        }

        protected void onFailure(FailureCode failureCode){

            downloadListener.onFailure();
        }

        //文件下载方法，会发送各种类型的事件
        void sendResponseMessage(InputStream is){
            RandomAccessFile randomAccessFile = null;
            completeSize=0;
            try{
                byte[] buffer = new byte[1024];
                int length=-1;//读写长度
                int limit=0;
                randomAccessFile = new RandomAccessFile(filePath, "rwd");
                while((length = is.read(buffer))!=-1){
                    if(isDownload){
                        randomAccessFile.write(buffer, 0 ,length);
                        completeSize += length;
                        if(completeSize < currentLength){
                            Log.e("tag", "completeSize="+completeSize);
                            Log.e("tag", "currentLength="+currentLength);
                            progress = (int)(Float.parseFloat(getTwoPointFloatStr(completeSize/currentLength))*100);
                            Log.e("tag", "下载进度："+progress);
                            if(limit % 30==0 && progress <= 100){//隔30次更新一次notification
                                sendProgressChangedMessage(progress);
                            }
                            limit++;
                        }
                    }
                }
                sendFinishMessage();
            }catch(IOException e){
                sendFailureMessage(FailureCode.IO);
            }finally{
                try{
                    if(is!=null){
                        is.close();
                    }
                    if(randomAccessFile!=null){
                        randomAccessFile.close();
                    }
                }catch(IOException e){
                    sendFailureMessage(FailureCode.IO);
                }
            }
        }
    }
}
