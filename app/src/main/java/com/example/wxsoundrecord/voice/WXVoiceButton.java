package com.example.wxsoundrecord.voice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 只是用来显示微信语音动画的一个控件
 * <p>
 * 在录音的时候可以通过 {@link #addVoiceSize(int)} 来设置一个音量条的高度
 * <p>
 * 如果不使用该控件的时候需要使用{@link #quit()} 方法来停止显示，避免内存泄露
 */
public class WXVoiceButton extends View {

    private final int MIN_VOICE_SIZE = 20;
    private Paint linePaint;

    List<DrawLine> drawLines = new ArrayList<>();
    private int LINE_WIDTH = 4; //音量条的宽度
    private int LINE_SPACE = 4; //两条之间的距离

    LineHandler lineHandler;

    private int DURATION = 600; //动画时间
    private Interpolator mInterpolator = new BounceInterpolator();


    private volatile boolean quit = false;

    public WXVoiceButton(Context context) {
        super(context);
    }

    public WXVoiceButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#000000")); //线的颜色
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);



        buildDrawLines();
        new Thread(() -> {
            Looper.prepare();
            lineHandler = new LineHandler(Looper.myLooper());
            Looper.loop();
        }).start();

    }


    private static class DrawLine {
        RectF rectF;
        int maxSize;
        int lineSize;
        boolean small = true;//是否缩小模式，
        float rotas = 1.0f;
        float timeCompletion = 0;//时间完成度，返回在0-1
        int duration = 0;
    }


    /**
     * 每条音频线能显示的最大值与showVoiceSize的比值
     */
    float[] ratios = new float[]{0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.5f, 0.3f, 0.5f, 0.8f, 1.0f, 0.8f, 0.5f, 0.3f, 0.5f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f};

    private void buildDrawLines() {
        drawLines.clear();
        for (float ratio : ratios) {
            int maxSize = (int) (MIN_VOICE_SIZE * ratio);
            RectF rect = new RectF(-LINE_WIDTH / 2, -maxSize / 2, LINE_WIDTH / 2, maxSize / 2);
            DrawLine drawLine = new DrawLine();
            drawLine.maxSize = maxSize;
            drawLine.rectF = rect;
            drawLine.lineSize = new Random().nextInt(maxSize);
            drawLine.rotas = ratio;
            //通过设置不同的时间完成度，让每个音量条有不同的初始值。可以实现参差不齐的效果
            // drawLine.timeCompletion = ratio;
            drawLine.duration = (int) (DURATION * (1.0f / ratio));
            drawLines.add(drawLine);
        }
    }


    private boolean toBig = true;

    /**
     * 设置音量大小，其实就是音量条显示的高度（单位像素)
     *
     * @param voiceSize
     */
    public void addVoiceSize(int voiceSize) {
        Log.d("song_test","voiceSize = "+voiceSize);
        Message message = Message.obtain();
        message.obj = voiceSize;
        message.what = WHAT_CHANGE_VOICE_SIZE;
        lineHandler.sendMessage(message);
        if (toBig) {
            //开始变大
            lineHandler.removeMessages(WHAT_BIG);
            lineHandler.sendEmptyMessage(WHAT_BIG);
            toBig = false;
        }
    }

    private static final int WHAT_ANIMATION = 1;//驱动动画的事件
    private static final int WHAT_BIG = 2;//驱动变宽的事件
    private static final int WHAT_CHANGE_VOICE_SIZE = 3;//驱动音量条高低变化的事件
    private static final int WHAT_CHECK_VOICE = 4;//驱动进入巡检模式的事件

    private static int[] checkModeItemSize = new int[]{20, 30, 40, 50, 40, 30, 20}; //监听线高度
    private int checkStarIndex = 0;
    private boolean isCheckMode = false;

    private class LineHandler extends Handler {
        public LineHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void dispatchMessage(@androidx.annotation.NonNull Message msg) {
//            Log.i("xxx", "wx dispatchMessage what=" + msg.what);
            switch (msg.what) {
                case WHAT_ANIMATION:
                    for (DrawLine drawLine : drawLines) {
                        float timeStep = 16.0f / drawLine.duration;//时间增长步长
                        float timeCompletion = drawLine.timeCompletion;//时间完成度
                        timeCompletion += timeStep;//更新时间完成度
                        float animationCompletion = mInterpolator.getInterpolation(timeCompletion);//获取动画完成度。
                        //int maxLineSize = (int) (drawLine.rotas * showVoiceSize);
                        int lineSize = 0;
                        //更新音量条的高度
                        if (drawLine.small) {
                            //变小
                            lineSize = (int) ((1 - animationCompletion) * drawLine.maxSize);

                        } else {
                            lineSize = (int) (animationCompletion * drawLine.maxSize);
                        }
                        if (timeCompletion >= 1) {
                            //完成了单边的缩小，或增长，则切换模式
                            drawLine.small = !drawLine.small;
                            drawLine.timeCompletion = 0;
                        } else {
                            drawLine.timeCompletion = timeCompletion;//更新时间完成度。
                        }

                        lineSize = Math.max(lineSize, 10);//对最小值进行过滤
                        RectF rectF = drawLine.rectF;
                        rectF.top = (-lineSize * 1.0f / 6) - 4;
                        rectF.bottom = (lineSize * 1.0f / 6) + 4;

                        drawLine.lineSize = lineSize;
                    }
                    invalidate();//更新UI
                    removeMessages(WHAT_ANIMATION);
                    sendEmptyMessageDelayed(WHAT_ANIMATION, 16);
                    break;
                case WHAT_CHANGE_VOICE_SIZE:
                    int voiceSize = (int) msg.obj;
                    if (voiceSize < 30) {
                        if (!isCheckMode) {
                            //声音太小进入监听模式
                            isCheckMode = true;
                            checkStarIndex = drawLines.size() - 1;
                            sendEmptyMessage(WHAT_CHECK_VOICE);
                        }
                        return;
                    } else {
                        //退出监听模式
                        isCheckMode = false;
                        removeMessages(WHAT_CHECK_VOICE);
                    }


                    for (DrawLine drawLine : drawLines) {
                        drawLine.timeCompletion = 0;
                        drawLine.small = false;
                        drawLine.maxSize = (int) (drawLine.rotas * voiceSize);
                    }
                    sendEmptyMessage(WHAT_ANIMATION);
                    break;
                case WHAT_CHECK_VOICE:
                    if (!isCheckMode) {
                        //判断是否是监听模式
                        return;
                    }

                    removeMessages(WHAT_ANIMATION);//移除之前的上下的动画模式
                    //由于声音太小，显示波浪动画表示正在检查声音
                    for (int i = 0; i < drawLines.size(); i++) {
                        DrawLine drawLine = drawLines.get(i);
                        int index = i - checkStarIndex;
                        if (index >= 0 && index < checkModeItemSize.length) {
                            drawLine.lineSize = checkModeItemSize[index];
                        } else {
                            drawLine.lineSize = 10;
                        }
                        drawLine.rectF.top = (-drawLine.lineSize / 6) - 4;
                        drawLine.rectF.bottom = (drawLine.lineSize / 6) + 4;

                    }
                    checkStarIndex--;
                    if (checkStarIndex == -checkModeItemSize.length) {
                        checkStarIndex = drawLines.size() - 1;
                    }

                    invalidate();//更新UI
                    removeMessages(WHAT_CHECK_VOICE);
                    sendEmptyMessageDelayed(WHAT_CHECK_VOICE, 100);

                    break;

            }

        }
    }

    public void quit() {
        lineHandler.getLooper().quit();
        quit = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        lineHandler.sendEmptyMessage(WHAT_ANIMATION);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(getWidth() / 2, getHeight() / 2);
            float offsetX = (drawLines.size() - 1) * 1.0f / 2 * (LINE_WIDTH + LINE_SPACE);
            canvas.translate(-offsetX, 0);
            for (DrawLine drawLine : drawLines) {
                canvas.drawRoundRect(drawLine.rectF, 5, 5, linePaint);
                canvas.translate(LINE_WIDTH + LINE_SPACE, 0);
            }
        canvas.restore();
    }

}
