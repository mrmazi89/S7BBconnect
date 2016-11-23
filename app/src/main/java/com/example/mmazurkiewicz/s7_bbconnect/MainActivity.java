package com.example.mmazurkiewicz.s7_bbconnect;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import Moka7.*;

public class MainActivity extends ActionBarActivity {

    S7Client client = new S7Client();
    Handler PlcConnHandler = new Handler();
    SQLiteDatabase sourceDB= null;

    public boolean ConnectionState;
    public boolean ConnectionStart;

    //Dane SQLite
    static final String srcTable = "Blueprint";
    static final String colID = "ID";
    static final String colName = "Name";
    static final String colType = "Type";
    static final String colComment = "Comment";


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Tworzenie folderu dla źródeł danych TXT/AWL
        File folder = new File(Environment.getExternalStorageDirectory() +
                File.separator + "S7BBsources");

        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }

        File srcTXT = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/SourceTXT.txt");
        File srcAWL = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/SourceAWL.awl");

        if(!srcTXT.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(srcTXT, true);
                out.write("Tutaj wklej źródło TXT lub zastąp ten plik gotowym źródłem.".getBytes());
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Błąd zapisu do srcTXT!");
            }
        }

        if(!srcAWL.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(srcAWL, true);
                out.write("Tutaj wklej źródło AWL lub zastąp ten plik gotowym źródłem.".getBytes());
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Błąd zapisu do srcAWL!");
            }
        }
        //***

        //Otwieranie bazy danych wzorca
        sourceDB = this.openOrCreateDatabase("srcDB", MODE_PRIVATE, null);
        sourceDB.execSQL("DROP TABLE IF EXISTS "+srcTable+";");//Czyszczenie bazy danych wzorca

        PlcConnHandler.post(CyclicPlc);     //Start operacji cyklicznych
    }

    public void connect2PLC(View v){
        ConnectionStart = true;
    }

    public void disconnectPLC(View v){
        ConnectionStart = false;
    }



    //Operacje cykliczne
    private Runnable CyclicPlc = new Runnable() {
        @Override
        public void run() {

            //Wywołanie odczytu ze sterownika
            new PlcReader().execute("");

            //Wystawienie informacji o statusie połączenia
            TextView connSTS = (TextView) findViewById(R.id.tV_ConnectionState);
            if(ConnectionStart){
                if(ConnectionState){
                    connSTS.setText("Połączenie OK! :)");
                    connSTS.setBackgroundColor(Color.GREEN);
                }else{
                    connSTS.setText("Brak połączenia! :/");
                    connSTS.setBackgroundColor(Color.RED);
                }
            }else{
                connSTS.setText("Brak połączenia! :/");
                connSTS.setBackgroundColor(Color.RED);
            }

            //Zdefiniowanie cyklu handlera 1000ms
            PlcConnHandler.postDelayed(CyclicPlc, 200);
        }
    };

    private class PlcReader extends AsyncTask<String, Void, String> {
        String ret = "";

        @Override
        protected String doInBackground(String... params) {
            if (ConnectionStart){
                try {
                    client.SetConnectionType(S7.S7_BASIC);
                    int res = client.ConnectTo("192.168.10.20",0,2); //S7-314


                    if (res == 0) {//connection ok
                        byte[] data = new byte[4];
                        res = client.ReadArea(S7.S7AreaDB,10,4,4, data);//Read DB10.DBB0 - REAL
                        ret = "Wartość REAL DB10.DBD0: " + S7.GetFloatAt(data,0);
                        ConnectionState = true;
                    } else {
                        ret = "Błąd: " + S7Client.ErrorText(res);
                        ConnectionState = false;
                    }
                    client.Disconnect();
                } catch (Exception e) {
                    ret = "Wyjątek: " + e.toString();
                    Thread.interrupted();
                }
                return "wykonano";
            }
            return "wykonano";
        }

        @Override
        protected void onPostExecute(String result){
            TextView txout = (TextView) findViewById(R.id.tV_Wartosc);
            txout.setText(ret);
        }
    }

//    public void openFolder(View v)    //Otwieranie folderu
//    {
//        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//        Uri uri = Uri.parse(Environment.getExternalStorageDirectory().getPath() + "123app/");
//        intent.setDataAndType(uri, "text/csv");
//        startActivity(Intent.createChooser(intent, "Open folder"));
//    }

    public void sendMail(View v)
    {
        String filename="StoreFile.txt";
        File filelocation = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), filename);
        Uri path = Uri.fromFile(filelocation);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
// set the type to 'email'
        emailIntent .setType("vnd.android.cursor.dir/email");
        String to[] = {"mazurkiewicz.m89@gmail.com"};
        emailIntent .putExtra(Intent.EXTRA_EMAIL, to);
// the attachment
        emailIntent .putExtra(Intent.EXTRA_STREAM, path);
// the mail subject
        emailIntent .putExtra(Intent.EXTRA_SUBJECT, "S7-BBconnect-test mail");
        startActivity(Intent.createChooser(emailIntent , "Send email..."));
    }

    public void readTXTsource (View v)
    {

//Get the text file
        File srcTXT = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/SourceTXT.txt");

//Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(srcTXT));
            String line;
            String lineSQL;

            sourceDB.execSQL("DROP TABLE IF EXISTS "+srcTable+";");
            sourceDB.execSQL("CREATE TABLE IF NOT EXISTS "+srcTable+" ("    //Tworzenie tabeli SQL
                    +colID+" INTEGER PRIMARY KEY AUTOINCREMENT, "
                    +colName+" STRING, "+colType+" STRING, "+colComment+" STRING);");

            while ((line = br.readLine()) != null) {
                lineSQL = line.trim();
                sourceDB.execSQL("INSERT INTO "+ srcTable
                        + " ("+colName+")" + " VALUES ('"+lineSQL+"');");
                text.append(line);
                text.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }

//Find the view by its id
        TextView tv = (TextView)findViewById(R.id.tV_Content);

//Set the text
        tv.setText(text.toString());
    }

    public void readAWLsource (View v)
    {

//Get the text file
        File srcAWL = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/SourceAWL.AWL");

//Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(srcAWL));
            String line;
            String lineSQL;
            boolean var2SQL = false;

            sourceDB.execSQL("DROP TABLE IF EXISTS "+srcTable+";");
            sourceDB.execSQL("CREATE TABLE IF NOT EXISTS "+srcTable+" ("    //Tworzenie tabeli SQL
                    +colID+" INTEGER PRIMARY KEY AUTOINCREMENT, "
                    +colName+" STRING, "+colType+" STRING, "+colComment+" STRING);");

            while ((line = br.readLine()) != null) {
                lineSQL = line.trim();


                if (lineSQL.contains("END_STRUCT")) {
                    var2SQL = false;
                }

                if (var2SQL) {
                    //Wyodrębnianie poszczególnych elementów-nazwa, typ zmiennej
                    String[] parts1 = lineSQL.split("\\:");
                    String part1_1 = parts1[0].trim();
                    String part2_1 = "";
                    String[] parts2 = null;
                    if(parts1.length > 1) {
                    String part1_2 = parts1[1].trim();
                    parts2 = part1_2.split("\\;");
                    part2_1 = parts2[0].trim();
                    }


                    sourceDB.execSQL("INSERT INTO " + srcTable
                            + " (" + colName +", "+colType+", "+colComment+ ")"
                            + " VALUES ('" +part1_1+"', '"+part2_1+"', '');");

                    line = part1_1 +"   "+ part2_1;
                    text.append(line);
                    text.append('\n');
                }


                if (lineSQL.contains("STRUCT") & !lineSQL.contains("END_STRUCT")) {
                    var2SQL = true;
                }
            }
            br.close();
        }
        catch (IOException e) {
            //You'll need to add proper error handling here
        }

//Find the view by its id
        TextView tv = (TextView)findViewById(R.id.tV_Content);

//Set the text
        tv.setText(text.toString());
    }
// test versji 101b
}
