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
import android.widget.EditText;
import android.widget.TextView;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import Moka7.*;

public class MainActivity extends ActionBarActivity {

    S7Client client = new S7Client();       //Klient połączenia Android <<>> PLC
    Handler PlcConnHandler = new Handler(); //Handler operacji cyklicznych
    SQLiteDatabase sourceDB= null;          //Baza danych SQLite

    Date curTime = new Date();
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
    String curTimeStr = format.format(curTime);

    public boolean ConnectionState;
    public boolean ConnectionStart;

    //Dane SQLite
    static final String srcTable = "Blueprint";
    static final String colID = "ID";
    static final String colName = "Name";
    static final String colType = "Type";
    static final String colComment = "Comment";

    //Szablon w postaci lokalnych zmiennych
    public String [] arrAddress = new String[99];
    public String [] arrName = new String[99];
    public String [] arrType = new String[99];
    public String [] arrValue = new String[99];
    public int [] arrStartByte = new int[99];
    public int [] arrAmountByte = new int[99];
    public int [] arrBitOfByte = new int[99];
    public int nElements = 0;       //liczba zmiennych w szablonie zapisu
    //Dane pomocnicze dla wzorca AWL
    public String AddrElms_DBnr = "";
    public String AddrElms_VarSpec = "";

    String retValue = "";
    StringBuilder text4 = new StringBuilder();

    //Dane połączenia ze sterownikiem
    public int plcIP1 = 0;
    public int plcIP2 = 0;
    public int plcIP3 = 0;
    public int plcIP4 = 0;
    public int plcRack = 0;
    public int plcSlot = 0;
    public String adresIP = "";

    File file = new File(Environment.getExternalStorageDirectory() + "/StoreFile.txt");


    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        file.delete();
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
        sourceDB.execSQL("DROP TABLE IF EXISTS archiwumDB;");//Czyszczenie bazy danych wzorca

        PlcConnHandler.post(CyclicPlc);     //Start operacji cyklicznych
    }

    public void connect2PLC(View v){ //Funkcja nawiązania połączenia ze sterownikiem PLC
        //Przepisanie do zmiennych podanego adresu IP sterownika PLC, jeśli poszczególne człony niepuste
        EditText ETIP1 = (EditText) findViewById(R.id.eT_IP1);
        if(!ETIP1.getText().toString().matches("")) {
            plcIP1 = Integer.valueOf(ETIP1.getText().toString());
        }
        EditText ETIP2 = (EditText) findViewById(R.id.eT_IP2);
        if(!ETIP2.getText().toString().matches("")) {
            plcIP2 = Integer.valueOf(ETIP2.getText().toString());
        }
        EditText ETIP3 = (EditText) findViewById(R.id.eT_IP3);
        if(!ETIP3.getText().toString().matches("")) {
            plcIP3 = Integer.valueOf(ETIP3.getText().toString());
        }
        EditText ETIP4 = (EditText) findViewById(R.id.eT_IP4);
        if(!ETIP4.getText().toString().matches("")) {
            plcIP4 = Integer.valueOf(ETIP4.getText().toString());
        }
        EditText ETRack = (EditText) findViewById(R.id.eT_Rack);
        if(!ETRack.getText().toString().matches("")) {
            plcRack = Integer.valueOf(ETRack.getText().toString());
        }
        EditText ETSlot = (EditText) findViewById(R.id.eT_Slot);
        if(!ETSlot.getText().toString().matches("")) {
            plcSlot = Integer.valueOf(ETSlot.getText().toString());
        }
        adresIP = plcIP1+"."+plcIP2+"."+plcIP3+"."+plcIP4;
        if(!ETIP1.getText().toString().matches("") & !ETIP2.getText().toString().matches("") & !ETIP3.getText().toString().matches("")
                & !ETIP4.getText().toString().matches("") & !ETRack.getText().toString().matches("") & !ETSlot.getText().toString().matches("") ) {//Ustawienie rozpoczęcia połączenia
            ConnectionStart = true;
        }else{                          //Błąd danych dla połączenia z PLC
            TextView txout = (TextView) findViewById(R.id.tV_Wartosc);
            txout.setText("Niepoprawne dane połączenia z PLC");
        }
    }

    public void disconnectPLC(View v){//Funkcja zakończenia połączenia ze sterownikiem PLC
        ConnectionStart = false;
    }



    //Operacje cykliczne
    private Runnable CyclicPlc = new Runnable() {
        @Override
        public void run() {

            //Wywołanie odczytu ze sterownika
            new PlcReader().execute("");
            //Aktualizacja wartości czasuaktualnego
            curTime = new Date();
            curTimeStr = format.format(curTime);

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
            PlcConnHandler.postDelayed(CyclicPlc, 1000);
        }
    };

    private class PlcReader extends AsyncTask<String, Void, String> {
        String ret = "";

        @Override
        protected String doInBackground(String... params) {
            if (ConnectionStart){
                try {
                    client.SetConnectionType(S7.S7_BASIC);
                    int res = client.ConnectTo(adresIP,plcRack,plcSlot); //S7-314
                    ConnectionState = client.Connected;
                    int n = 0;
                    retValue = "";


                    if (res == 0) {//connection ok
                        while (arrType [n] != null && client.Connected) {
                            byte[] data = new byte[arrAmountByte[n]];
                            res = client.ReadArea(S7.S7AreaDB, Integer.valueOf(AddrElms_DBnr), arrStartByte [n], arrAmountByte [n], data);//Read
                            ret = "";

                            if(arrType [n].contains("REAL")) {
                                retValue = "" + S7.GetFloatAt(data, 0);
                            }
                            if(arrType [n].contains("DWORD")) {
                                retValue = "" + S7.GetDWordAt(data, 0);
                            }
                            if(arrType [n].contains("DINT")) {
                                retValue = "" + S7.GetDIntAt(data, 0);
                            }
                            if(arrType [n].contains("WORD")) {
                                retValue = "" + S7.GetShortAt(data, 0);
                            }
                            if(arrType [n].contains("INT")) {
                                retValue = "" + S7.GetShortAt(data, 0);
                            }
                            if(arrType [n].contains("BYTE")) {
                                retValue = "" + data[0];
                            }
                            if(arrType [n].contains("BOOL")) {
                                retValue = "" + S7.GetBitAt(data, 0,arrBitOfByte[n]);
                            }
                            text4.append(retValue);
                            text4.append('\n');
                            arrValue [n] = retValue;
                            n=n+1;
                        }
                    } else {
                        ret = "Błąd: " + S7Client.ErrorText(res);
                    }
                    client.Disconnect();

                } catch (Exception e) {
                    ret = "Wyjątek: " + e.toString();
                    e.printStackTrace();
                    System.out.println("Błąd odczytu z PLC");
                    Thread.interrupted();
                }
                return "wykonano";
            }
            return "wykonano";

        }

        @Override
        protected void onPostExecute(String result){
            if(ConnectionStart){
                TextView txout = (TextView) findViewById(R.id.tV_Wartosc);
                txout.setText(ret);
                TextView tv4 = (TextView)findViewById(R.id.tV_Content_4);
                tv4.setText(text4.toString());
                text4.setLength(0);
                int n = 0;
                String sqlColumns = "";
                String sqlValues = "";
                String TxtNewLine = "";

                while (arrType [n] != null){
                    sqlColumns = sqlColumns +", "+ arrName[n];
                    sqlValues = sqlValues + ", '"+ arrValue[n] +"'";
                    TxtNewLine =TxtNewLine + arrValue[n]+"; ";
                    n=n+1;
                }

                sourceDB.execSQL("INSERT INTO archiwumDB ( Czas"+ sqlColumns + ")"
                        + " VALUES ('"+curTimeStr+"'" + sqlValues + ");");

                String toWriteData = TxtNewLine +"\n";

                try {
                    FileOutputStream out = new FileOutputStream(file, true);
                    out.write(toWriteData.toString().getBytes());
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Błąd zapisu do pliku!");
                }
            }
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
        if(!ConnectionStart) {
//Get the text file
            File srcTXT = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/SourceTXT.txt");

//Read text from file
            StringBuilder text = new StringBuilder();

            try {
                BufferedReader br = new BufferedReader(new FileReader(srcTXT));
                String line;
                String lineSQL;

                sourceDB.execSQL("DROP TABLE IF EXISTS " + srcTable + ";");
                sourceDB.execSQL("CREATE TABLE IF NOT EXISTS " + srcTable + " ("    //Tworzenie tabeli SQL
                        + colID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + colName + " STRING, " + colType + " STRING, " + colComment + " STRING);");

                while ((line = br.readLine()) != null) {
                    lineSQL = line.trim();
                    sourceDB.execSQL("INSERT INTO " + srcTable
                            + " (" + colName + ")" + " VALUES ('" + lineSQL + "');");
                    text.append(line);
                    text.append('\n');
                }
                br.close();
            } catch (IOException e) {
                //You'll need to add proper error handling here
            }

//Find the view by its id
            TextView tv = (TextView) findViewById(R.id.tV_Content_1);

//Set the text
            tv.setText(text.toString());
        }
    }

    public void readAWLsource (View v)
    {
        file = new File(Environment.getExternalStorageDirectory() + "/StoreFile.txt");
        if(!ConnectionStart) {
            EditText ETDBnr = (EditText) findViewById(R.id.eT_DBnumber);
            if (!ETDBnr.getText().toString().matches("")) {
                AddrElms_DBnr = String.valueOf(ETDBnr.getText().toString());
            }
//Get the text file
            File srcAWL = new File(Environment.getExternalStorageDirectory() + "/S7BBsources/DB"+AddrElms_DBnr+".awl");

//Read text from file
            StringBuilder text1 = new StringBuilder();
            StringBuilder text2 = new StringBuilder();
            StringBuilder text3 = new StringBuilder();

            try {
                BufferedReader br = new BufferedReader(new FileReader(srcAWL));
                String line;
                String lineSQL;
                boolean var2SQL = false;
                int n = 0;

                sourceDB.execSQL("DROP TABLE IF EXISTS " + srcTable + ";");
                sourceDB.execSQL("CREATE TABLE IF NOT EXISTS " + srcTable + " ("    //Tworzenie tabeli SQL
                        + colID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + colName + " STRING, " + colType + " STRING, " + colComment + " STRING);");

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
                        if (parts1.length > 1) {
                            String part1_2 = parts1[1].trim();
                            parts2 = part1_2.split("\\;");
                            part2_1 = parts2[0].trim();
                        }


                        sourceDB.execSQL("INSERT INTO " + srcTable
                                + " (" + colName + ", " + colType + ", " + colComment + ")"
                                + " VALUES ('" + part1_1 + "', '" + part2_1 + "', '');");

                        //line = part1_1 +"   "+ part2_1;
                        arrName[n] = part1_1;
                        arrType[n] = part2_1;
                        text2.append(arrName[n]);
                        text2.append('\n');
                        text3.append(arrType[n]);
                        text3.append('\n');
                        //Wypełnienie kolumny adresów zmiennych


                        if (arrType[n].contains("REAL") || arrType[n].contains("DWORD") || arrType[n].contains("DINT")) {
                            arrAmountByte[n] = 4;
                            if (n == 0) {
                                arrStartByte[n] = 0;
                            } else {
                                arrStartByte[n] = arrStartByte[n - 1] + arrAmountByte[n - 1];
                            }
                            AddrElms_VarSpec = ("D " + arrStartByte[n]);
                        }

                        if ((arrType[n].contains("WORD") || arrType[n].contains("INT")) && !arrType[n].contains("DWORD") && !arrType[n].contains("DINT")) {
                            arrAmountByte[n] = 2;
                            if (n == 0) {
                                arrStartByte[n] = 0;
                            } else {
                                arrStartByte[n] = arrStartByte[n - 1] + arrAmountByte[n - 1];
                            }
                            AddrElms_VarSpec = ("W " + arrStartByte[n]);
                        }

                        if (arrType[n].contains("BYTE")) {
                            arrAmountByte[n] = 1;
                            if (n == 0) {
                                arrStartByte[n] = 0;
                            } else {
                                arrStartByte[n] = arrStartByte[n - 1] + arrAmountByte[n - 1];
                            }
                            AddrElms_VarSpec = ("B " + arrStartByte[n]);
                        }

                        if (arrType[n].contains("BOOL")) {
                            arrAmountByte[n] = 1;
                            if (n == 0) {
                                arrStartByte[n] = 0;
                            } else {
                                arrStartByte[n] = arrStartByte[n - 1] + arrAmountByte[n - 1];
                            }

                            if ((!arrType[n - 1].contains("BOOL") || arrBitOfByte[n - 1] == 7)) {
                                arrBitOfByte[n] = 0;
                            } else {
                                arrBitOfByte[n] = arrBitOfByte[n - 1] + 1;
                            }
                            AddrElms_VarSpec = ("X " + arrStartByte[n] + "." + arrBitOfByte[n]);
                        }


                        arrAddress[n] = ("DB" + AddrElms_DBnr + ".DB" + AddrElms_VarSpec);
                        text1.append(arrAddress[n]);
                        text1.append('\n');
                        nElements = n;
                        n = n + 1;
                    }


                    if (lineSQL.contains("STRUCT") & !lineSQL.contains("END_STRUCT")) {
                        var2SQL = true;
                    }

                }
                br.close();
            } catch (IOException e) {
                //You'll need to add proper error handling here
            }
            TextView tv1 = (TextView) findViewById(R.id.tV_Content_1);
            tv1.setText(text1.toString());
            TextView tv2 = (TextView) findViewById(R.id.tV_Content_2);
            tv2.setText(text2.toString());
            TextView tv3 = (TextView) findViewById(R.id.tV_Content_3);
            tv3.setText(text3.toString());

            sourceDB.execSQL("DROP TABLE IF EXISTS archiwumDB;");
            int n = 0;
            String SQLqueryAdd = "";
            String TxtNewLine = "";

            while(arrName [n] != null){
                SQLqueryAdd =  SQLqueryAdd + ", "+arrName [n]+" STRING";
                TxtNewLine = TxtNewLine +arrName[n]+ "; " ;
                n=n+1;
            }
            sourceDB.execSQL("CREATE TABLE IF NOT EXISTS archiwumDB (ID INTEGER PRIMARY KEY AUTOINCREMENT, Czas STRING"+SQLqueryAdd+");");

            String toWriteData = TxtNewLine +"\n";

            try {
                FileOutputStream out = new FileOutputStream(file, true);
                out.write(toWriteData.toString().getBytes());
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Błąd zapisu do pliku!");
            }
        }
    }

}
