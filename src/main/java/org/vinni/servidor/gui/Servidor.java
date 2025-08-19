package org.vinni.servidor.gui;

import javax.swing.*;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Servidor extends JFrame {
    private JTextArea logArea;
    private JTextField puertoField;
    private JButton iniciarBtn, apagarBtn;
    private ServerSocket tcpServerSocket;
    private DatagramSocket udpSocket;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private Map<String, Socket> clientesConectados = new ConcurrentHashMap<>();
    private Map<String, InetAddress> direccionesUDP = new ConcurrentHashMap<>();
    private Map<Integer, String> puertosUDP = new ConcurrentHashMap<>();

    private String downloadFolder = Config.get("downloadFolderServidor");

    public Servidor() {
        setTitle("Servidor de Chat");
        setSize(700, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setIconImage(new ImageIcon("server_icon.png").getImage());

        // Panel superior
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel puertoLbl = new JLabel("Puerto TCP:");
        puertoField = new JTextField("12345", 6);
        
        iniciarBtn = new JButton("Iniciar Servidor", new ImageIcon("start_icon.png"));
        iniciarBtn.addActionListener(e -> iniciarServidor());
        
        apagarBtn = new JButton("Apagar", new ImageIcon("stop_icon.png"));
        apagarBtn.setEnabled(false);
        apagarBtn.addActionListener(e -> apagarServidor());
        
        topPanel.add(puertoLbl);
        topPanel.add(puertoField);
        topPanel.add(iniciarBtn);
        topPanel.add(apagarBtn);
        
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
            
            log("Servidor iniciado en TCP:" + puerto + " UDP:" + (puerto + 1));
            iniciarBtn.setEnabled(false);
            apagarBtn.setEnabled(true);
        } catch (Exception e) {
            log("Error iniciando servidor: " + e.getMessage());
        }
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
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            String username = in.readUTF();
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
                        recibirArchivoTCP(in, fileDest, fileName, fileSize);
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
            log("Cliente desconectado: " + socket.getInetAddress());
        }
    }

    private void manejarConexionesUDP() {
    byte[] buffer = new byte[65535];
    try {
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            udpSocket.receive(packet);
            
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
                DataInputStream dis = new DataInputStream(bais);
                
                String tipo = dis.readUTF();
                String destino = dis.readUTF();
                String remitente = dis.readUTF();
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();
                
                // Calculamos el tamaño de los datos efectivos
                int dataLength = packet.getLength() - (dis.available() + 
                    tipo.getBytes().length + destino.getBytes().length + 
                    remitente.getBytes().length + fileName.getBytes().length + 8);
                
                threadPool.submit(() -> 
                    manejarArchivoUDP(dis, destino, remitente, fileName, fileSize, dataLength)
                );
            } catch (IOException e) {
                log("Error procesando paquete UDP: " + e.getMessage());
            }
        }
    } catch (IOException e) {
        if (!udpSocket.isClosed()) {
            log("Error en conexión UDP: " + e.getMessage());
        }
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

    private void apagarServidor() {
        try {
            if (tcpServerSocket != null) tcpServerSocket.close();
            if (udpSocket != null) udpSocket.close();
            threadPool.shutdown();
            log("Servidor apagado");
            iniciarBtn.setEnabled(true);
            apagarBtn.setEnabled(false);
        } catch (IOException e) {
            log("Error al apagar: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> 
            logArea.append("[" + new Date() + "] " + msg + "\n")
        );
    }

    private void recibirArchivoTCP(DataInputStream in, String destino, String fileName, long fileSize) {
    try {
        Path destPath = Paths.get(downloadFolder, fileName);
        
        // Evitar sobrescribir archivos existentes
        int counter = 1;
        String originalName = fileName;
        while (Files.exists(destPath)) {
            int dotIndex = originalName.lastIndexOf('.');
            String name = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
            String ext = dotIndex > 0 ? originalName.substring(dotIndex) : "";
            fileName = name + "(" + counter + ")" + ext;
            destPath = Paths.get(downloadFolder, fileName);
            counter++;
        }

        // Recibir el archivo en chunks
        try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
            byte[] buffer = new byte[4096];
            long remaining = fileSize;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }

        log("Archivo recibido de " + destino + ": " + fileName);
        
        // Reenviar al destinatario si está conectado
        Socket destSocket = clientesConectados.get(destino);
        if (destSocket != null) {
            try {
                DataOutputStream destOut = new DataOutputStream(destSocket.getOutputStream());
                destOut.writeUTF("FILE");
                destOut.writeUTF(destino); // Remitente original
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
                log("Error reenviando archivo a " + destino);
            }
        }
    } catch (IOException e) {
        log("Error recibiendo archivo: " + e.getMessage());
    }
}

private void manejarArchivoUDP(DataInputStream dis, String destino, String remitente, 
                             String fileName, long fileSize, int dataLength) {
    try {
        // Verificar si el destinatario existe
        if (!clientesConectados.containsKey(destino)) {
            log("Destinatario " + destino + " no encontrado para archivo UDP");
            return;
        }

        // Crear directorio de descargas si no existe
        Path downloadDir = Paths.get(downloadFolder);
        if (!Files.exists(downloadDir)) {
            Files.createDirectories(downloadDir);
        }

        // Generar nombre único para el archivo
        Path destPath = downloadDir.resolve(fileName);
        int counter = 1;
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        
        while (Files.exists(destPath)) {
            fileName = baseName + "(" + counter + ")" + extension;
            destPath = downloadDir.resolve(fileName);
            counter++;
        }

        // Escribir el archivo
        try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
            byte[] fileData = new byte[dataLength];
            dis.readFully(fileData);
            fos.write(fileData);
        }

        log("Archivo UDP recibido de " + remitente + ": " + fileName + " (" + 
            (dataLength/1024) + " KB)");
        
        // Notificar al destinatario
        Socket destSocket = clientesConectados.get(destino);
        if (destSocket != null && !destSocket.isClosed()) {
            try {
                DataOutputStream destOut = new DataOutputStream(destSocket.getOutputStream());
                destOut.writeUTF("UDP_FILE");
                destOut.writeUTF(remitente);
                destOut.writeUTF(fileName);
                destOut.writeLong(fileSize);
                destOut.writeUTF(destPath.toString());
                log("Notificado a " + destino + " sobre archivo UDP");
            } catch (IOException e) {
                log("Error notificando a " + destino + ": " + e.getMessage());
            }
        }
    } catch (IOException e) {
        log("Error procesando archivo UDP: " + e.getMessage());
    } finally {
        try {
            dis.close();
        } catch (IOException e) {
            log("Error cerrando stream: " + e.getMessage());
        }
    }
}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Servidor().setVisible(true));
    }
}