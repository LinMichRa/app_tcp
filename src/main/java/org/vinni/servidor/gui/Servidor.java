package org.vinni.servidor.gui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

public class Servidor extends JFrame {
    private JTextArea logArea;
    private JTextField puertoField;
    private JButton iniciarBtn, apagarBtn, verClientesBtn;
    private ServerSocket tcpServerSocket;
    private DatagramSocket udpSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // Cambio aquí
    private Map<String, Socket> clientesConectados = new ConcurrentHashMap<>();
    private Map<String, InetAddress> direccionesUDP = new ConcurrentHashMap<>();
    private Map<Integer, String> puertosUDP = new ConcurrentHashMap<>();
    
    // Constante para UDP
    private static final int UDP_PACKET_SIZE = 60000;
    
    // Mapa para transferencias activas
    private Map<String, Map<String, FileTransfer>> transferenciasActivas = new ConcurrentHashMap<>();
    
    // Mapa para almacenar información de archivos
    private Map<String, List<ArchivoServidor>> archivosServidor = new ConcurrentHashMap<>();

    private String downloadFolder = Config.get("downloadFolderServidor");
    
    // Clase para manejar transferencias de archivos
    private class FileTransfer {
        String remitente;
        String destinatario;
        String fileName;
        long fileSize;
        int totalPackets;
        int receivedPackets;
        byte[][] packetData;
        long creationTime;
        
        public FileTransfer(String remitente, String destinatario, String fileName, 
                           long fileSize, int totalPackets) {
            this.remitente = remitente;
            this.destinatario = destinatario;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.totalPackets = totalPackets;
            this.receivedPackets = 0;
            this.packetData = new byte[totalPackets][];
            this.creationTime = System.currentTimeMillis();
        }
    }
    
    // Clase interna para archivos en servidor
    private class ArchivoServidor {
        String remitente;
        String destinatario;
        String nombre;
        String ruta;
        long tamaño;
        Date fecha;
        boolean esUDP;
        
        public ArchivoServidor(String remitente, String destinatario, String nombre, 
                              String ruta, long tamaño, boolean esUDP) {
            this.remitente = remitente;
            this.destinatario = destinatario;
            this.nombre = nombre;
            this.ruta = ruta;
            this.tamaño = tamaño;
            this.fecha = new Date();
            this.esUDP = esUDP;
        }
    }

    public Servidor() {
        setTitle("Servidor de Chat");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel superior
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel puertoLbl = new JLabel("Puerto TCP:");
        puertoField = new JTextField("12345", 6);
        
        iniciarBtn = new JButton("Iniciar");
        iniciarBtn.addActionListener(e -> iniciarServidor());
        
        apagarBtn = new JButton("Apagar");
        apagarBtn.setEnabled(false);
        apagarBtn.addActionListener(e -> apagarServidor());
        
        verClientesBtn = new JButton("Clientes");
        verClientesBtn.setEnabled(false);
        verClientesBtn.addActionListener(e -> mostrarClientesConectados());
        
        topPanel.add(puertoLbl);
        topPanel.add(puertoField);
        topPanel.add(iniciarBtn);
        topPanel.add(apagarBtn);
        topPanel.add(verClientesBtn);
        
        // Área de logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Registro de Actividad"));
        
        add(topPanel, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void iniciarServidor() {
        try {
            int puerto = Integer.parseInt(puertoField.getText());
            
            // Iniciar TCP
            tcpServerSocket = new ServerSocket(puerto);
            threadPool.submit(this::manejarConexionesTCP);
            
            // Iniciar UDP
            udpSocket = new DatagramSocket(puerto + 1);
            threadPool.submit(this::manejarConexionesUDP);
            
            // Iniciar limpieza de transferencias
            limpiarTransferenciasAntiguas();
            
            log("Servidor iniciado en TCP:" + puerto + " UDP:" + (puerto + 1));
            iniciarBtn.setEnabled(false);
            apagarBtn.setEnabled(true);
            verClientesBtn.setEnabled(true);
        } catch (Exception e) {
            log("Error iniciando servidor: " + e.getMessage());
        }
    }

    private void limpiarTransferenciasAntiguas() {
        scheduler.scheduleAtFixedRate(() -> { // Cambio aquí
            synchronized (transferenciasActivas) {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<String, Map<String, FileTransfer>>> it = 
                    transferenciasActivas.entrySet().iterator();
                
                while (it.hasNext()) {
                    Map.Entry<String, Map<String, FileTransfer>> entry = it.next();
                    FileTransfer transfer = entry.getValue().values().iterator().next();
                    
                    // Eliminar transferencias con más de 5 minutos de antigüedad
                    if (currentTime - transfer.creationTime > 5 * 60 * 1000) {
                        log("Eliminando transferencia expirada: " + transfer.fileName);
                        it.remove();
                    }
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void manejarConexionesTCP() {
        try {
            while (!tcpServerSocket.isClosed()) {
                Socket clienteSocket = tcpServerSocket.accept();
                threadPool.submit(() -> manejarClienteTCP(clienteSocket));
            }
        } catch (IOException e) {
            if (!tcpServerSocket.isClosed()) {
                log("Error TCP: " + e.getMessage());
            }
        }
    }

    private void manejarClienteTCP(Socket socket) {
        String username = null;
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            username = in.readUTF();
            clientesConectados.put(username, socket);
            log("Cliente conectado: " + username);
            broadcastUsuarios();
            
            while (true) {
                String tipo = in.readUTF();
                switch (tipo) {
                    case "MSG":
                        String destino = in.readUTF();
                        String mensaje = in.readUTF();
                        enviarMensajeTCP(destino, username, mensaje);
                        break;
                    case "FILE":
                        String fileDest = in.readUTF();
                        String fileName = in.readUTF();
                        long fileSize = in.readLong();
                        recibirArchivoTCP(in, fileDest, username, fileName, fileSize);
                        break;
                    case "UDP_INFO":
                        int puertoUDP = in.readInt();
                        direccionesUDP.put(username, socket.getInetAddress());
                        puertosUDP.put(puertoUDP, username);
                        log("Cliente " + username + " listo para UDP en puerto " + puertoUDP);
                        break;
                }
            }
        } catch (IOException e) {
            if (username != null) {
                log("Cliente desconectado: " + username);
                clientesConectados.remove(username);
                direccionesUDP.remove(username);
                broadcastUsuarios();
            }
        }
    }

    private void manejarConexionesUDP() {
        byte[] buffer = new byte[UDP_PACKET_SIZE];
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                
                threadPool.submit(() -> procesarPaqueteUDP(packet));
            }
        } catch (IOException e) {
            if (!udpSocket.isClosed()) {
                log("Error en conexión UDP: " + e.getMessage());
            }
        }
    }

    private void procesarPaqueteUDP(DatagramPacket packet) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
            DataInputStream dis = new DataInputStream(bais);
            
            String tipo = dis.readUTF();
            String destino = dis.readUTF();
            String remitente = dis.readUTF();
            String fileName = dis.readUTF();
            long fileSize = dis.readLong();
            int totalPackets = dis.readInt();
            int packetNumber = dis.readInt();
            int dataLength = dis.readInt();
            
            // Leer datos del paquete
            byte[] packetData = new byte[dataLength];
            dis.readFully(packetData);
            
            String transferKey = remitente + "_" + fileName;
            
            synchronized (transferenciasActivas) {
                if (!transferenciasActivas.containsKey(transferKey)) {
                    // Nueva transferencia
                    FileTransfer transfer = new FileTransfer(remitente, destino, fileName, 
                                                           fileSize, totalPackets);
                    transfer.packetData[packetNumber] = packetData;
                    transfer.receivedPackets++;
                    
                    Map<String, FileTransfer> transfers = new HashMap<>();
                    transfers.put(transferKey, transfer);
                    transferenciasActivas.put(transferKey, transfers);
                    
                    log("Iniciando transferencia UDP: " + fileName + " (" + totalPackets + " paquetes)");
                } else {
                    // Transferencia existente
                    FileTransfer transfer = transferenciasActivas.get(transferKey).get(transferKey);
                    transfer.packetData[packetNumber] = packetData;
                    transfer.receivedPackets++;
                    
                    // Verificar si se completó la transferencia
                    if (transfer.receivedPackets == transfer.totalPackets) {
                        completarTransferenciaUDP(transfer);
                        transferenciasActivas.remove(transferKey);
                    }
                }
            }
            
        } catch (IOException e) {
            log("Error procesando paquete UDP: " + e.getMessage());
        }
    }

    private void completarTransferenciaUDP(FileTransfer transfer) {
        try {
            // Crear directorio de descargas si no existe
            Path downloadDir = Paths.get(downloadFolder);
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
            }

            // Generar nombre único para el archivo
            Path destPath = downloadDir.resolve(transfer.fileName);
            int counter = 1;
            String originalName = transfer.fileName;
            while (Files.exists(destPath)) {
                int dotIndex = originalName.lastIndexOf('.');
                String name = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
                String ext = dotIndex > 0 ? originalName.substring(dotIndex) : "";
                transfer.fileName = name + "(" + counter + ")" + ext;
                destPath = downloadDir.resolve(transfer.fileName);
                counter++;
            }

            // Reconstruir el archivo
            try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                for (int i = 0; i < transfer.totalPackets; i++) {
                    if (transfer.packetData[i] != null) {
                        fos.write(transfer.packetData[i]);
                    }
                }
            }

            // Registrar archivo en servidor
            ArchivoServidor archivo = new ArchivoServidor(transfer.remitente, transfer.destinatario, 
                transfer.fileName, destPath.toString(), transfer.fileSize, true);
            archivosServidor.computeIfAbsent(transfer.destinatario, k -> new ArrayList<>()).add(archivo);
            
            log("Transferencia UDP completada: " + transfer.fileName + " de " + transfer.remitente);
            
            // Notificar al destinatario
            Socket destSocket = clientesConectados.get(transfer.destinatario);
            if (destSocket != null && !destSocket.isClosed()) {
                try {
                    DataOutputStream destOut = new DataOutputStream(destSocket.getOutputStream());
                    destOut.writeUTF("UDP_FILE");
                    destOut.writeUTF(transfer.remitente);
                    destOut.writeUTF(transfer.fileName);
                    destOut.writeLong(transfer.fileSize);
                    destOut.writeUTF(destPath.toString());
                    log("Notificado a " + transfer.destinatario + " sobre archivo UDP");
                } catch (IOException e) {
                    log("Error notificando a " + transfer.destinatario + ": " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            log("Error completando transferencia UDP: " + e.getMessage());
        }
    }

    private void recibirArchivoTCP(DataInputStream in, String destino, String remitente, String fileName, long fileSize) {
        try {
            // Crear carpeta de descargas si no existe
            Path downloadDir = Paths.get(downloadFolder);
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
            }
            
            Path destPath = downloadDir.resolve(fileName);
            
            // Manejar nombres duplicados
            int counter = 1;
            String originalName = fileName;
            while (Files.exists(destPath)) {
                int dotIndex = originalName.lastIndexOf('.');
                String name = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
                String ext = dotIndex > 0 ? originalName.substring(dotIndex) : "";
                fileName = name + "(" + counter + ")" + ext;
                destPath = downloadDir.resolve(fileName);
                counter++;
            }

            // Recibir el archivo en chunks
            try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                byte[] buffer = new byte[4096];
                long remaining = fileSize;
                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            // Registrar archivo en servidor
            ArchivoServidor archivo = new ArchivoServidor(remitente, destino, fileName, 
                destPath.toString(), fileSize, false);
            archivosServidor.computeIfAbsent(destino, k -> new ArrayList<>()).add(archivo);
            
            log("Archivo TCP recibido de " + remitente + " para " + destino + ": " + fileName);
            
            // Reenviar al destinatario si está conectado
            Socket destSocket = clientesConectados.get(destino);
            if (destSocket != null) {
                try {
                    DataOutputStream destOut = new DataOutputStream(destSocket.getOutputStream());
                    destOut.writeUTF("FILE");
                    destOut.writeUTF(remitente);
                    destOut.writeUTF(fileName);
                    destOut.writeLong(fileSize);
                    
                    try (FileInputStream fis = new FileInputStream(destPath.toFile())) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            destOut.write(buffer, 0, bytesRead);
                        }
                    }
                    log("Archivo reenviado a " + destino);
                } catch (IOException e) {
                    log("Error reenviando archivo a " + destino + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Error recibiendo archivo: " + e.getMessage());
        }
    }

    private void enviarMensajeTCP(String destino, String remitente, String mensaje) {
        try {
            Socket socketDest = clientesConectados.get(destino);
            if (socketDest != null) {
                DataOutputStream out = new DataOutputStream(socketDest.getOutputStream());
                out.writeUTF("MSG");
                out.writeUTF(remitente);
                out.writeUTF(mensaje);
                log("Mensaje de " + remitente + " enviado a " + destino);
            }
        } catch (IOException e) {
            log("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void broadcastUsuarios() {
        String usuarios = String.join(",", clientesConectados.keySet());
        for (Socket socket : clientesConectados.values()) {
            try {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("USERLIST");
                out.writeUTF(usuarios);
            } catch (IOException e) {
                log("Error enviando lista de usuarios: " + e.getMessage());
            }
        }
    }
    
    private void mostrarClientesConectados() {
        JDialog dialog = new JDialog(this, "Clientes Conectados", true);
        dialog.setSize(400, 300);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationRelativeTo(this);
        
        // Crear tabla de clientes
        String[] columnNames = {"Usuario", "Dirección IP", "Estado"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        
        // Llenar la tabla con clientes
        for (Map.Entry<String, Socket> entry : clientesConectados.entrySet()) {
            String usuario = entry.getKey();
            Socket socket = entry.getValue();
            String ip = socket.getInetAddress().getHostAddress();
            String estado = socket.isConnected() ? "Conectado" : "Desconectado";
            
            model.addRow(new Object[]{usuario, ip, estado});
        }
        
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());
        
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(btnCerrar);
        
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void apagarServidor() {
        try {
            if (tcpServerSocket != null) tcpServerSocket.close();
            if (udpSocket != null) udpSocket.close();
            threadPool.shutdown();
            scheduler.shutdown(); // Apagar el scheduler también
            log("Servidor apagado");
            iniciarBtn.setEnabled(true);
            apagarBtn.setEnabled(false);
            verClientesBtn.setEnabled(false);
        } catch (IOException e) {
            log("Error al apagar: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> 
            logArea.append("[" + new Date() + "] " + msg + "\n")
        );
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Servidor().setVisible(true));
    }
}