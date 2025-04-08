package com.example.pantalla;

import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/* loaded from: classes3.dex */
public class MainActivity extends AppCompatActivity {
    private static final int PUERTO = 8080;
    private static final String TAG = "Pantalla";
    private Handler handler;
    private String ipInfo = "";
    private ServerSocket serverSocket;
    private TextView tvStatus;
    private boolean isRunning = false;
    private int horas = 0;
    private int minutos = 0;
    private int segundos = 0;
    private int periodo = 0;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                segundos++;
                if (segundos == 60) {
                    segundos = 0;
                    minutos++;
                    if (minutos == 60) {
                        minutos = 0;
                        horas++;
                    }
                }
                actualizarDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(1024, 1024);
        getWindow().addFlags(128);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(4102);
        setContentView(R.layout.activity_main);
        this.tvStatus = (TextView) findViewById(R.id.tvStatus);
        this.handler = new Handler(Looper.getMainLooper());
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService("wifi");
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipInt = wifiInfo.getIpAddress();
            String devIp = String.format("%d.%d.%d.%d", Integer.valueOf(ipInt & 255), Integer.valueOf((ipInt >> 8) & 255), Integer.valueOf((ipInt >> 16) & 255), Integer.valueOf((ipInt >> 24) & 255));
            this.ipInfo = devIp;
            actualizarStatus("IP: " + this.ipInfo);
        } catch (Exception e) {
            this.ipInfo = "Error: " + e.getMessage();
            actualizarStatus(this.ipInfo);
        }
        new Thread(new Runnable() { // from class: com.example.pantalla.MainActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.iniciarServidor();
            }
        }).start();
    }

    private void actualizarDisplay() {
        TextView horaDigit1 = findViewById(R.id.hora_digit1);
        TextView horaDigit2 = findViewById(R.id.hora_digit2);
        TextView minutoDigit1 = findViewById(R.id.minuto_digit1);
        TextView minutoDigit2 = findViewById(R.id.minuto_digit2);
        TextView periodoDigit1 = findViewById(R.id.periodo_digit1);
        TextView periodoDigit2 = findViewById(R.id.periodo_digit2);

        horaDigit1.setText(String.valueOf(horas / 10));
        horaDigit2.setText(String.valueOf(horas % 10));
        minutoDigit1.setText(String.valueOf(minutos / 10));
        minutoDigit2.setText(String.valueOf(minutos % 10));
        periodoDigit1.setText(String.valueOf(periodo / 10));
        periodoDigit2.setText(String.valueOf(periodo % 10));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void iniciarServidor() {
        String str;
        try {
            actualizarStatus("IP: " + this.ipInfo);
            this.serverSocket = new ServerSocket();
            this.serverSocket.setReuseAddress(true);
            this.serverSocket.bind(new InetSocketAddress("0.0.0.0", PUERTO));
            while (true) {
                Socket clientSocket = null;
                BufferedReader entrada = null;
                try {
                    try {
                        clientSocket = this.serverSocket.accept();
                        Log.d(TAG, "Cliente conectado desde: " + clientSocket.getInetAddress());
                        mostrarMensajeConexion("Conexión exitosa / IP: " + clientSocket.getInetAddress().getHostAddress(), true);
                        entrada = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        while (!Thread.interrupted() && clientSocket.isConnected() && !clientSocket.isClosed()) {
                            String mensaje = entrada.readLine();
                            if (mensaje == null) {
                                Log.d(TAG, "Cliente desconectado");
                                break;
                            }
                            Log.d(TAG, "Mensaje recibido: " + mensaje);
                            procesarComando(mensaje);
                        }
                        try {
                            entrada.close();
                            if (clientSocket != null) {
                                clientSocket.close();
                                mostrarMensajeConexion("Desconectado del cliente", false);
                            }
                        } catch (Exception e) {
                            str = "Error al cerrar recursos: " + e.getMessage();
                            Log.e(TAG, str);
                        }
                    } finally {
                        if (entrada != null) {
                            try {
                            } catch (Exception e2) {
                            }
                        }
                    }
                } catch (Exception e3) {
                    Log.e(TAG, "Error en la conexión: " + e3.getMessage());
                    mostrarMensajeConexion("Desconectado del cliente", false);
                    if (entrada != null) {
                        try {
                            entrada.close();
                        } catch (Exception e4) {
                            str = "Error al cerrar recursos: " + e4.getMessage();
                            Log.e(TAG, str);
                        }
                    }
                    if (clientSocket != null) {
                        clientSocket.close();
                        mostrarMensajeConexion("Desconectado del cliente", false);
                    }
                }
            }
        } catch (Exception e5) {
            Log.e(TAG, "Error fatal: " + e5.getMessage());
            mostrarMensajeConexion("Error fatal: " + e5.getMessage(), false);
        }
    }

    private void procesarComando(String mensaje) {
        // Primero intentamos procesar como comando
        switch (mensaje) {
            case "START":
                isRunning = true;
                timerHandler.post(timerRunnable);
                return;
            case "STOP":
                isRunning = false;
                timerHandler.removeCallbacks(timerRunnable);
                return;
            case "RESET":
                isRunning = false;
                timerHandler.removeCallbacks(timerRunnable);
                horas = 0;
                minutos = 0;
                segundos = 0;
                periodo = 0;
                actualizarDisplay();
                return;
            case "PERIODO":
                periodo = (periodo + 1) % 100;
                actualizarDisplay();
                return;
        }

        // Si no es un comando, intentamos procesar como valores
        try {
            String[] datosStr = mensaje.split(",");
            if (datosStr.length == 6) {
                final int[] datos = new int[datosStr.length];
                for (int i = 0; i < datosStr.length; i++) {
                    datos[i] = Integer.parseInt(datosStr[i]);
                }
                
                // Actualizamos los valores
                horas = datos[0] * 10 + datos[1];
                minutos = datos[2] * 10 + datos[3];
                periodo = datos[4] * 10 + datos[5];
                
                // Actualizamos la pantalla
                actualizarDisplay();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al procesar mensaje: " + e.getMessage());
        }
    }

    private void actualizarStatus(final String mensaje) {
        this.handler.post(new Runnable() { // from class: com.example.pantalla.MainActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m68lambda$actualizarStatus$1$comexamplepantallaMainActivity(mensaje);
            }
        });
    }

    /* renamed from: lambda$actualizarStatus$1$com-example-pantalla-MainActivity, reason: not valid java name */
    /* synthetic */ void m68lambda$actualizarStatus$1$comexamplepantallaMainActivity(String mensaje) {
        this.tvStatus.setText(mensaje.replace("\n", ""));
        this.tvStatus.setBackgroundColor(getResources().getColor(R.color.status_background));
        this.tvStatus.setTextColor(getResources().getColor(R.color.black));
        this.tvStatus.setVisibility(0);
    }

    private void mostrarMensajeConexion(final String mensaje, final boolean esExitoso) {
        this.handler.post(new Runnable() { // from class: com.example.pantalla.MainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m70x836fa349(mensaje, esExitoso);
            }
        });
    }

    /* renamed from: lambda$mostrarMensajeConexion$3$com-example-pantalla-MainActivity, reason: not valid java name */
    /* synthetic */ void m70x836fa349(String mensaje, final boolean esExitoso) {
        this.tvStatus.setText(mensaje);
        this.tvStatus.setBackgroundColor(getResources().getColor(esExitoso ? R.color.success_green : R.color.error_red));
        this.tvStatus.setTextColor(getResources().getColor(R.color.white));
        this.tvStatus.setVisibility(0);
        this.handler.postDelayed(new Runnable() { // from class: com.example.pantalla.MainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m69x1c96e388(esExitoso);
            }
        }, 3000L);
    }

    /* renamed from: lambda$mostrarMensajeConexion$2$com-example-pantalla-MainActivity, reason: not valid java name */
    /* synthetic */ void m69x1c96e388(boolean esExitoso) {
        if (esExitoso) {
            this.tvStatus.setVisibility(8);
            return;
        }
        this.tvStatus.setBackgroundColor(getResources().getColor(R.color.status_background));
        this.tvStatus.setTextColor(getResources().getColor(R.color.black));
        this.tvStatus.setText("IP: " + this.ipInfo);
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        try {
            if (this.serverSocket != null && !this.serverSocket.isClosed()) {
                this.serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.activity.ComponentActivity, android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(4102);
        Log.d(TAG, "Configuración cambiada. Orientación: " + (newConfig.orientation == 2 ? "Horizontal" : "Vertical"));
    }
}
