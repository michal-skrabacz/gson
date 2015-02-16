package com.qm.gson.realm.demo;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class MainActivity extends ActionBarActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Gson gson = new GsonBuilder().create();

        TestBean tb = new TestBean();

        String json = gson.toJson(tb);

        Log.d("XXX", json);
    }

}
