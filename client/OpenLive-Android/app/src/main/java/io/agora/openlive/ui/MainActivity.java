package io.agora.openlive.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import io.agora.openlive.R;
import io.agora.openlive.model.ConstantApp;
import io.agora.rtc2.Constants;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void initUIandEvent() {
        EditText textRoomName = (EditText) findViewById(R.id.room_name);
        EditText textRtmPeerId = (EditText) findViewById(R.id.rtm_peerId);
        findViewById(R.id.button_join).setEnabled(textRoomName.getText().toString().length() != 0);

        textRoomName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean isEmpty = s.toString().isEmpty();
                findViewById(R.id.button_join).setEnabled(!isEmpty);
            }
        });
    }


    @Override
    protected void deInitUIandEvent() {
        event().removeEventHandler(this);
    }

    public void onClickJoin(View view) {
        // show dialog to choose role
        MainActivity.this.forwardToLiveRoom(Constants.CLIENT_ROLE_AUDIENCE);

    }

    public void forwardToLiveRoom(int cRole) {
        final EditText v_room = (EditText) findViewById(R.id.room_name);
        final EditText rtm_peerId = (EditText) findViewById(R.id.rtm_peerId);
        String room = v_room.getText().toString();
        String peerId = rtm_peerId.getText().toString();

        Intent i = new Intent(MainActivity.this, LiveRoomActivity.class);
        i.putExtra(ConstantApp.ACTION_KEY_CROLE, cRole);
        i.putExtra(ConstantApp.ACTION_KEY_ROOM_NAME, room);
        i.putExtra(ConstantApp.ACTION_KEY_RTM_PEERID,peerId);


        startActivity(i);
    }


    @Override
    public void workThreadInited(){
        event().addEventHandler(this);
    }



}
