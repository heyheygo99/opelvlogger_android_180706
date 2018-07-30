package kr.re.eslab.opelvlogger;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import static kr.re.eslab.opelvlogger.MainActivity.UART_PROFILE_CONNECTED;
import static kr.re.eslab.opelvlogger.MainActivity.countDownTimerFlag;
import static kr.re.eslab.opelvlogger.MainActivity.mService;
import static kr.re.eslab.opelvlogger.MainActivity.mState;

/**
 * Created by DH_Han on 2018-07-02.
 */

public class ExtractFragment extends Fragment {

    /* 이름 : MILLISINFUTURE                                                            */
    /* 기능 : Count Timer 총 실행 시간(ms)                                              */
    protected static final int MILLISINFUTURE = 10500;

    /* 이름 : MILLISINFUTURE                                                            */
    /* 기능 : Count Timer 총 실행 시간(ms)                                              */
    protected static final int COUNT_DOWN_INTERVAL = 100;

    /* 이름 : extract_state_Count                                                       */
    /* 기능 : 현재 Activity로 추출하려는 상태의 갯수                                    */
    /* 본 Activity를 이용하여 "기어" 기능을 추출하고자 한다면                           */
    /* P R N D 총 4개의 상태를 살펴야 하므로 해당 값을 4로 설정                         */
    protected int extract_state_Count;

    /* 이름 : current_state_Count                                                       */
    /* 기능 : 현재 Activity로 추출 진행 중인 현재 단계 상태                             */
    protected int current_state_Count = 1;

    protected CountDownTimer countDownTimer;

    /* 이름 : touchEnable                                                               */
    /* 기능 : 현재 Activity가 표현하는 View에 Touch 가능 여부를 표시                    */
    protected boolean touchEnable;

    protected TextView noticeTextView;
    protected ImageView stateImageView;
    protected ConstraintLayout extractConstraintLayout;

    /* 이름 : startAnimation                                                            */
    /* 기능 : Blink Animation                                                           */
    /* 정의 파일 : blink_pointer.xml                                                    */
    protected Animation startAnimation;

    protected ArrayList<Integer> drawableArrayList = new ArrayList<Integer>();
    protected ArrayList<String> noticeArrayList = new ArrayList<String>();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        touchEnable = false;
        countDownTimerFlag = false;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_extract, container, false);

        noticeTextView = (TextView) view.findViewById(R.id.noticeTextView);
        stateImageView = (ImageView) view.findViewById(R.id.stateImageView);
        extractConstraintLayout
                = (ConstraintLayout) view.findViewById(R.id.extractConstraintLayout);

        startAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.blink_pointer);

        stateImageView.setBackgroundDrawable(getResources().getDrawable(drawableArrayList.get(0)));
        noticeTextView.setText(noticeArrayList.get(0));

        noticeTextView.setVisibility(View.VISIBLE);
        stateImageView.setVisibility(View.VISIBLE);

        extractConstraintLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if ( mState == UART_PROFILE_CONNECTED) {
                    int eventAction = event.getAction();
                    if (eventAction == MotionEvent.ACTION_DOWN && touchEnable) {
                        Log.d("TEST", "Touch");
                        touchEnable = false;

                        // state == 0 : P
                        // state == 1 : R
                        // state == 2 : N
                        // state == 3 : D
                        if (current_state_Count == 1) {
                            try {
                                extract_start();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (current_state_Count <= extract_state_Count) {
                            try {
                                extract_progress();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if (current_state_Count == extract_state_Count+1) {
                            try {
                                extract_filter();

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return true;
                    } else if (eventAction == MotionEvent.ACTION_MOVE && touchEnable) {
                        return false;
                    } else if (eventAction == MotionEvent.ACTION_UP && touchEnable) {
                        return false;
                    }
                }

                else {
                    Toast.makeText(getActivity(), "Please Connect BLE to Surelogger", Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        });

        return view;
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    /* 이름 : extract_start Method                                                  */
    /* 기능 : 추출 시작 method                                                          */
    protected void extract_start( ) throws IOException {
        Log.d("TEST","First");

        String message = "EXTRACT_START";
        byte[] value = message.getBytes("UTF-8");
        mService.writeRXCharacteristic(value);
        stateImageView.startAnimation(startAnimation);

        // CountDownTimer
        // 3초 대기 후 onFinish Method 실행, 추출 진행 단계 current_state_Count 값을 올림
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
                stateImageView.clearAnimation();
                stateImageView.setBackgroundDrawable(getResources().getDrawable(drawableArrayList.get(1)));
                //noticeMessage.setText("후진 기어(R) 설정 후 화면을 터치하세요");
                noticeTextView.setText(noticeArrayList.get(1));
                current_state_Count++;
                countDownTimerFlag = false;
            }
        }.start();
    }

    /* 이름 : extract_progress Method                                               */
    /* 기능 : 추출 진행 method                                                          */
    protected void extract_progress() throws IOException {

        String message = "EXTRACT_PROGRESS";
        byte[] value = message.getBytes("UTF-8");
        mService.writeRXCharacteristic(value);
        if ( current_state_Count > 1) {
            stateImageView.startAnimation(startAnimation);

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
                    stateImageView.clearAnimation();

                    if(current_state_Count == extract_state_Count) {
                        stateImageView.setBackgroundDrawable(getResources().getDrawable(R.drawable.extract_end));
                        noticeTextView.setText("화면을 터치하여\n 결과를 확인하세요");
                    }

                    else if(current_state_Count == 2) {
                        stateImageView.setBackgroundDrawable(getResources().getDrawable(drawableArrayList.get(2)));
                        noticeTextView.setText(noticeArrayList.get(2));
                    }
                    else if(current_state_Count == 3) {
                        stateImageView.setBackgroundDrawable(getResources().getDrawable(drawableArrayList.get(3)));
                        noticeTextView.setText(noticeArrayList.get(3));
                    }
                    current_state_Count++;
                    countDownTimerFlag = false;
                }
            }.start();
        }
    }

    /* 이름 : extract_filter Method                                                 */
    /* 기능 : filter를 사용하여 최종적으로 id를 추출함                                           */
    protected void extract_filter() throws IOException {
        String message = "EXTRACT_FILTER";
        byte[] value = new byte[0];
        value = message.getBytes("UTF-8");
        mService.writeRXCharacteristic(value);
    }
}
