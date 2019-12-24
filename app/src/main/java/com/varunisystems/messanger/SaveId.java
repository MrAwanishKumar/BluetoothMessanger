package com.varunisystems.messanger;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

public class SaveId {
    SharedPreferences sharedPreferences;
    Context context;
    private String id;
    private String macAddress;

    public SaveId(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences("id", Context.MODE_PRIVATE);
    }

    public String getId() {
        id = sharedPreferences.getString("id", "");
        return id;
    }

    public void setId(String id) {
        this.id = id;
        sharedPreferences.edit().putString("id", id).commit();
    }

    public void removeUserData() {
        sharedPreferences.edit().clear().apply();
        Toast.makeText(context, "Removed Id", Toast.LENGTH_LONG).show();
    }

    public String getMacAddress() {
        macAddress = sharedPreferences.getString("macAddress", "");
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
        sharedPreferences.edit().putString("macAddress", macAddress).commit();
    }
}
