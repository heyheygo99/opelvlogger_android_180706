package kr.re.eslab.opelvlogger;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.app.Fragment;
import android.os.CountDownTimer;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static kr.re.eslab.opelvlogger.MainActivity.UART_PROFILE_CONNECTED;
import static kr.re.eslab.opelvlogger.MainActivity.countDownTimerFlag;
import static kr.re.eslab.opelvlogger.MainActivity.mService;
import static kr.re.eslab.opelvlogger.MainActivity.mState;

public class ExtractGearFragment extends ExtractFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        extract_state_Count = 4;
        current_state_Count = 1;

        drawableArrayList.add(0,R.drawable.extract_gear_p);
        drawableArrayList.add(1,R.drawable.extract_gear_r);
        drawableArrayList.add(2,R.drawable.extract_gear_n);
        drawableArrayList.add(3,R.drawable.extract_gear_d);

        noticeArrayList.add(0,"주차 기어(P) 설정 후\n 화면을 터치하세요");
        noticeArrayList.add(1,"후진 기어(R) 설정 후\n 화면을 터치하세요");
        noticeArrayList.add(2,"후진 기어(N) 설정 후\n 화면을 터치하세요");
        noticeArrayList.add(3,"주행 기어(D) 설정 후\n 화면을 터치하세요");

        String message = "EXTRACT 0 1 "+ extract_state_Count;
        byte[] value = new byte[0];
        try {
            value = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        mService.writeRXCharacteristic(value);

        countDownTimer = new CountDownTimer(MILLISINFUTURE, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {
                // CountDownTimer Tick을 갱신하면서 진행 되는 부분
                if(countDownTimerFlag) {
                    cancel();
                    onFinish();
                }
            }
            public void onFinish() {
                touchEnable = true;
                countDownTimerFlag = false;
            }
        }.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return super.onCreateView( inflater,  container, savedInstanceState);
    }

    @Override
    public void onStop(){
        super.onStop();
    }

}
