package com.example.pantalla;

import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    private boolean isAscending = true;
    private boolean primeraConexionRealizada = false;
    private int currentSeconds = 0;
    private int maxSeconds = 0;
    private int presetSeconds = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView[] timeDigits = new TextView[4];
    private TextView[] periodDigits = new TextView[2];
    private long startTime;
    private long pausedTime = 0;
    private long suspendedTime = 0;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 100; // 100ms para actualizaciones suaves
    private boolean isPaused = false;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            long currentTime = SystemClock.elapsedRealtime();
            long deltaTime = currentTime - startTime;
            
            // Convertir milisegundos a segundos
            int secondsElapsed = (int) (deltaTime / 1000);
            
            if (isAscending) {
                int previousSeconds = currentSeconds;
                currentSeconds = secondsElapsed;
                
                // Solo incrementar cuando pasamos por un múltiplo del preset
                if (presetSeconds > 0 && 
                    previousSeconds / presetSeconds != currentSeconds / presetSeconds && 
                    currentSeconds > 0) {
                    incrementPeriod();
                }
            } else {
                // En modo descendente
                if (maxSeconds > 0) {
                    currentSeconds = maxSeconds - secondsElapsed;

                    if (currentSeconds <= 0) {
                        // Incrementar periodo cuando llega a 0
                        incrementPeriod();
                        // Mostrar que el conteo llegó a 00:00
                        updateDisplayTime();
                        // Agregar un pequeño retardo antes de pausar
                        handler.postDelayed(() -> {
                            // Pausar el cronómetro
                            suspendTimer();
                            // Reiniciar el botón de inicio
                        }, 200); // Retardo de 500ms
                        return;
                    }
                }
            }

            // Actualizar UI
            handler.post(() -> {
                updateDisplayTime();
            });

            if (isRunning) {
                timerHandler.postDelayed(this, 100);
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
        
        // Inicializar referencias a los dígitos
        timeDigits[0] = findViewById(R.id.hora_digit1);
        timeDigits[1] = findViewById(R.id.hora_digit2);
        timeDigits[2] = findViewById(R.id.minuto_digit1);
        timeDigits[3] = findViewById(R.id.minuto_digit2);
        
        periodDigits[0] = findViewById(R.id.periodo_digit1);
        periodDigits[1] = findViewById(R.id.periodo_digit2);

        this.tvStatus = findViewById(R.id.tvStatus);
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
        
        new Thread(this::iniciarServidor).start();
    }

    private void updateDisplayTime() {
        int minutes = currentSeconds / 60;
        int seconds = currentSeconds % 60;
        
        timeDigits[0].setText(String.valueOf(minutes / 10));
        timeDigits[1].setText(String.valueOf(minutes % 10));
        timeDigits[2].setText(String.valueOf(seconds / 10));
        timeDigits[3].setText(String.valueOf(seconds % 10));
    }

    private void incrementPeriod() {
        int currentPeriod = Integer.parseInt(periodDigits[0].getText().toString()) * 10 +
                           Integer.parseInt(periodDigits[1].getText().toString());
        currentPeriod++;
        if (currentPeriod > 99) currentPeriod = 0;

        periodDigits[0].setText(String.valueOf(currentPeriod / 10));
        periodDigits[1].setText(String.valueOf(currentPeriod % 10));
    }

    private void updateMaxTime() {
        int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                     Integer.parseInt(timeDigits[1].getText().toString());
        int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                     Integer.parseInt(timeDigits[3].getText().toString());
        maxSeconds = minutes * 60 + seconds;
        currentSeconds = maxSeconds;
        updateDisplayTime();
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            if (isAscending) {
                // En modo ascendente, comenzar desde el valor establecido en el reloj
                int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                             Integer.parseInt(timeDigits[1].getText().toString());
                int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                             Integer.parseInt(timeDigits[3].getText().toString());
                currentSeconds = minutes * 60 + seconds;
                startTime = SystemClock.elapsedRealtime() - (currentSeconds * 1000L);
            } else {
                // En modo descendente, usar el tiempo del preset si está disponible
                if (presetSeconds > 0) {
                    currentSeconds = presetSeconds;
                    maxSeconds = presetSeconds;
                } else {
                    // Si no hay preset, usar el tiempo actual del reloj
                    int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                                 Integer.parseInt(timeDigits[1].getText().toString());
                    int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                                 Integer.parseInt(timeDigits[3].getText().toString());
                    currentSeconds = minutes * 60 + seconds;
                    maxSeconds = currentSeconds;
                }
                startTime = SystemClock.elapsedRealtime();
            }
            timerHandler.postDelayed(timerRunnable, 100);
        }
    }

    private void suspendTimer() {
        if (isRunning) {
            isRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            suspendedTime = SystemClock.elapsedRealtime() - startTime;
        }
    }

    private void pauseTimer() {
        if (isRunning) {
            isRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            if (isAscending) {
                pausedTime = SystemClock.elapsedRealtime() - startTime;
            } else {
                pausedTime = currentSeconds; // Guarda los segundos restantes
            }
            isPaused = true;
        }
    }

    private void stopTimer() {
        isRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        currentSeconds = 0;
        periodDigits[0].setText("0");
        periodDigits[1].setText("0");
        for (TextView digit : timeDigits) {
            digit.setText("0");
        }
        maxSeconds = 0;
        suspendedTime = 0;
    }

    private void procesarComando(String mensaje) {
        String[] partes = mensaje.split("\\|");
        String comando = partes[0];

        switch (comando) {
            case "PLAY":
                // Actualizar currentSeconds y maxSeconds con los valores actuales del reloj
                int playMinutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                              Integer.parseInt(timeDigits[1].getText().toString());
                int playSeconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                              Integer.parseInt(timeDigits[3].getText().toString());
                currentSeconds = playMinutes * 60 + playSeconds;
                maxSeconds = currentSeconds;
                // Iniciar el temporizador
                isRunning = true;
                
                if (isAscending) {
                    // En modo ascendente, usar el tiempo actual del reloj
                    int minutes = Integer.parseInt(timeDigits[0].getText().toString()) * 10 +
                                Integer.parseInt(timeDigits[1].getText().toString());
                    int seconds = Integer.parseInt(timeDigits[2].getText().toString()) * 10 +
                                Integer.parseInt(timeDigits[3].getText().toString());
                    currentSeconds = minutes * 60 + seconds;
                    startTime = SystemClock.elapsedRealtime() - (currentSeconds * 1000L);
                } else {
                   // En modo descendente, usar el tiempo del preset
                    if(isPaused){
                        startTime = SystemClock.elapsedRealtime() - ((maxSeconds - currentSeconds) * 1000L);
                        isPaused = false;  
                    } else {
                        if(presetSeconds > 0){
                            currentSeconds = presetSeconds;
                            maxSeconds = presetSeconds;
                        }
                        startTime = SystemClock.elapsedRealtime();
                    }
                }
                
                timerHandler.postDelayed(timerRunnable, 100);
                break;
                
            case "PAUSE":
                pauseTimer();
                break;
                
            case "STOP":
                stopTimer();
                break;
                
            case "MODE":
                if (partes.length > 1) {
                    isAscending = partes[1].equals("ASC");
                    // Si cambiamos a modo descendente, actualizar el tiempo inicial
                    if (!isAscending && presetSeconds > 0) {
                        currentSeconds = presetSeconds;
                        maxSeconds = presetSeconds;
                        updateDisplayTime();
                    }
                }
                break;
                
            case "TIME":
                if (partes.length > 1) {
                    String[] timeValues = partes[1].split(",");
                    if (timeValues.length == 4) {
                        // Actualizar los valores del reloj
                        for (int i = 0; i < 4; i++) {
                            timeDigits[i].setText(timeValues[i]);
                        }
                        
                        // Actualizar currentSeconds para mantener consistencia
                        int mins = Integer.parseInt(timeValues[0]) * 10 + Integer.parseInt(timeValues[1]);
                        int secs = Integer.parseInt(timeValues[2]) * 10 + Integer.parseInt(timeValues[3]);
                        currentSeconds = mins * 60 + secs;
                    }
                }
                break;
                
            case "PRESET":
                if (partes.length > 1) {
                    String[] presetValues = partes[1].split(",");
                    if (presetValues.length == 4) {
                        // Calcular el tiempo preset en segundos
                        int mins = Integer.parseInt(presetValues[0]) * 10 + Integer.parseInt(presetValues[1]);
                        int secs = Integer.parseInt(presetValues[2]) * 10 + Integer.parseInt(presetValues[3]);
                        presetSeconds = mins * 60 + secs;
                        
                        // Si estamos en modo descendente, actualizar el tiempo inicial
                        if (!isAscending && presetSeconds > 0) {
                            currentSeconds = presetSeconds;
                            maxSeconds = presetSeconds;
                            updateDisplayTime();
                        }
                    }
                }
                break;
                
            case "PERIOD":
                if (partes.length > 1) {
                    String[] periodValues = partes[1].split(",");
                    if (periodValues.length == 2) {
                        periodDigits[0].setText(periodValues[0]);
                        periodDigits[1].setText(periodValues[1]);
                    }
                }
                break;
        }
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
                            final String mensajeFinal = mensaje;
                            handler.post(() -> procesarComando(mensajeFinal));
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

    private void actualizarStatus(final String mensaje) {
        this.handler.post(() -> {
        this.tvStatus.setText(mensaje.replace("\n", ""));
        this.tvStatus.setBackgroundColor(getResources().getColor(R.color.status_background));
        this.tvStatus.setTextColor(getResources().getColor(R.color.black));
        this.tvStatus.setVisibility(0);
        });
    }

    private void mostrarMensajeConexion(final String mensaje, final boolean esExitoso) {
        this.handler.post(() -> {
            // Si es la primera conexión exitosa, actualizar el flag
            if (esExitoso && !primeraConexionRealizada) {
                primeraConexionRealizada = true;
            }

            // Solo mostrar el mensaje si:
            // 1. Es una conexión exitosa (verde)
            // 2. Es una desconexión (rojo)
            if (esExitoso || !primeraConexionRealizada || mensaje.contains("Desconectado")) {
                this.tvStatus.setVisibility(View.VISIBLE);
        this.tvStatus.setBackgroundColor(getResources().getColor(esExitoso ? R.color.success_green : R.color.error_red));
        this.tvStatus.setTextColor(getResources().getColor(R.color.white));
                
                // Si ya hubo una primera conexión exitosa, solo mostrar "Conexión exitosa"
                if (esExitoso && primeraConexionRealizada) {
                    this.tvStatus.setText("Conexión exitosa");
                } else {
                    this.tvStatus.setText(mensaje);
                }

                // Si es una conexión/desconexión, ocultar después de 3 segundos
                if (mensaje.contains("Conexión") || mensaje.contains("Desconectado")) {
                    this.handler.postDelayed(() -> {
                        // Si no ha habido primera conexión, mostrar la IP
                        if (!primeraConexionRealizada) {
        this.tvStatus.setBackgroundColor(getResources().getColor(R.color.status_background));
        this.tvStatus.setTextColor(getResources().getColor(R.color.black));
        this.tvStatus.setText("IP: " + this.ipInfo);
                        } else {
                            // Si ya hubo primera conexión, ocultar el mensaje
                            this.tvStatus.setVisibility(View.GONE);
                        }
                    }, 3000L);
                }
            } else {
                // Si ya hubo primera conexión y no es un mensaje importante, mantener oculto
                this.tvStatus.setVisibility(View.GONE);
            }
        });
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
