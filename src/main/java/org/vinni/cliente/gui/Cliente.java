package org.vinni.cliente.gui;

import javax.swing.*;
import org.vinni.Config;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Desktop;

public class Cliente extends JFrame {
    private JTextArea chatArea;
    private JTextField mensajeField;
    private JButton conectarBtn, desconectarBtn, enviarBtn, enviarFileBtn, enviarVideoBtn, verArchivosBtn;
    private JList<String> userList;
    private DefaultListModel<String> listModel;
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private DataInputStream tcpIn;
    private DataOutputStream tcpOut;
    private String username;
    private String currentReceiver;
    
    // Constantes para UDP
    private static final int UDP_PACKET_SIZE = 60000;
    private static final int UDP_HEADER_SIZE = 1024;
    
    // Mapa para almacenar archivos recibidos
    private Map<String, List<ArchivoRecibido>> archivosRecibidos = new HashMap<>();
    
    // Clase interna para representar archivos recibidos
    private class ArchivoRecibido {
        String remitente;
        String nombre;
        String ruta;
        long tamaño;
        Date fecha;
        boolean esUDP;
        
        public ArchivoRecibido(String remitente, String nombre, String ruta, long tamaño, boolean esUDP) {
            this.remitente = remitente;
            this.nombre = nombre;
            this.ruta = ruta;
            this.tamaño = tamaño;
            this.fecha = new Date();
            this.esUDP = esUDP;
        }
        
        @Override
        public String toString() {
            return nombre + " (" + formatSize(tamaño) + ") - " + 
                   new SimpleDateFormat("dd/MM/yy HH:mm").format(fecha);
        }
    }

    public Cliente() {
        setTitle("Cliente de Chat");
        setSize(900, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Verificar configuración al iniciar
        verificarConfiguracion();

        // Panel superior
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextField usernameField = new JTextField("Usuario" + (new Random().nextInt(1000)), 10);
        JTextField hostField = new JTextField("localhost", 10);
        JTextField puertoField = new JTextField("12345", 5);
        
        conectarBtn = new JButton("Conectar");
        conectarBtn.addActionListener(e -> conectar(
            hostField.getText(), 
            Integer.parseInt(puertoField.getText()),
            usernameField.getText()
        ));
        
        desconectarBtn = new JButton("Desconectar");
        desconectarBtn.setEnabled(false);
        desconectarBtn.addActionListener(e -> desconectar());
        
        verArchivosBtn = new JButton("Archivos");
        verArchivosBtn.setEnabled(false);
        verArchivosBtn.addActionListener(e -> {
            verificarArchivosRecibidos();
            mostrarArchivosRecibidos();
        });
        
        topPanel.add(new JLabel("Usuario:"));
        topPanel.add(usernameField);
        topPanel.add(new JLabel("Host:"));
        topPanel.add(hostField);
        topPanel.add(new JLabel("Puerto:"));
        topPanel.add(puertoField);
        topPanel.add(conectarBtn);
        topPanel.add(desconectarBtn);
        topPanel.add(verArchivosBtn);
        
        // Panel central
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(200);
        
        // Lista de usuarios
        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.setBorder(BorderFactory.createTitledBorder("Usuarios Conectados"));
        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                currentReceiver = userList.getSelectedValue();
                chatArea.append("\n--- Chat con " + currentReceiver + " ---\n");
            }
        });
        userPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        splitPane.setLeftComponent(userPanel);
        
        // Área de chat
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(BorderFactory.createTitledBorder("Chat"));
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        splitPane.setRightComponent(chatPanel);
        
        // Panel inferior
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mensajeField = new JTextField();
        mensajeField.addActionListener(e -> enviarMensaje());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        enviarBtn = new JButton("Enviar");
        enviarBtn.setEnabled(false);
        enviarBtn.addActionListener(e -> enviarMensaje());
        
        enviarFileBtn = new JButton("Archivo");
        enviarFileBtn.setEnabled(false);
        enviarFileBtn.addActionListener(e -> enviarArchivo(false));
        
        enviarVideoBtn = new JButton("Video");
        enviarVideoBtn.setEnabled(false);
        enviarVideoBtn.addActionListener(e -> enviarArchivo(true));
        
        buttonPanel.add(enviarFileBtn);
        buttonPanel.add(enviarVideoBtn);
        buttonPanel.add(enviarBtn);
        
        bottomPanel.add(mensajeField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void verificarConfiguracion() {
        System.out.println("=== CONFIGURACIÓN INICIAL ===");
        System.out.println("Download folder: " + Config.get("downloadFolderCliente"));
        
        File downloadDir = new File(Config.get("downloadFolderCliente"));
        if (!downloadDir.exists()) {
            System.out.println("Creando directorio de descargas...");
            downloadDir.mkdirs();
        }
        System.out.println("Directorio existe: " + downloadDir.exists());
        System.out.println("Directorio escribible: " + downloadDir.canWrite());
        System.out.println("=============================");
    }

    private void conectar(String host, int puerto, String username) {
        try {
            this.username = username;
            
            // Conexión TCP
            tcpSocket = new Socket(host, puerto);
            tcpIn = new DataInputStream(tcpSocket.getInputStream());
            tcpOut = new DataOutputStream(tcpSocket.getOutputStream());
            tcpOut.writeUTF(username);
            
            // Conexión UDP
            udpSocket = new DatagramSocket();
            tcpOut.writeUTF("UDP_INFO");
            tcpOut.writeInt(udpSocket.getLocalPort());
            
            new Thread(this::recibirMensajes).start();
            log("Conectado al servidor");
            habilitarControles(true);
        } catch (IOException e) {
            log("Error de conexión: " + e.getMessage());
        }
    }

    private void recibirMensajes() {
        try {
            while (true) {
                String tipo = tcpIn.readUTF();
                switch (tipo) {
                    case "MSG":
                        String remitente = tcpIn.readUTF();
                        String mensaje = tcpIn.readUTF();
                        chatArea.append("[" + remitente + "]: " + mensaje + "\n");
                        break;
                    case "FILE":
                        String fileRemitente = tcpIn.readUTF();
                        String fileName = tcpIn.readUTF();
                        long fileSize = tcpIn.readLong();
                        recibirArchivoTCP(fileRemitente, fileName, fileSize);
                        break;
                    case "UDP_FILE":
                        String udpRemitente = tcpIn.readUTF();
                        String udpFileName = tcpIn.readUTF();
                        long udpFileSize = tcpIn.readLong();
                        String udpFilePath = tcpIn.readUTF();
                        manejarNotificacionUDP(udpRemitente, udpFileName, udpFileSize, udpFilePath);
                        break;
                    case "USERLIST":
                        String usuarios = tcpIn.readUTF();
                        actualizarListaUsuarios(usuarios);
                        break;
                }
            }
        } catch (IOException e) {
            log("Desconectado del servidor");
            habilitarControles(false);
        }
    }

    private void actualizarListaUsuarios(String usuarios) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (String user : usuarios.split(",")) {
                if (!user.equals(username) && !user.isEmpty()) {
                    listModel.addElement(user);
                }
            }
        });
    }

    private void enviarMensaje() {
        if (currentReceiver == null || currentReceiver.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione un destinatario");
            return;
        }
        
        String mensaje = mensajeField.getText().trim();
        if (mensaje.isEmpty()) {
            return;
        }
        
        try {
            tcpOut.writeUTF("MSG");
            tcpOut.writeUTF(currentReceiver);
            tcpOut.writeUTF(mensaje);
            
            chatArea.append("[Tú a " + currentReceiver + "]: " + mensaje + "\n");
            mensajeField.setText("");
        } catch (IOException e) {
            log("Error enviando mensaje: " + e.getMessage());
        }
    }

    private void enviarArchivo(boolean esVideo) {
        if (currentReceiver == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un destinatario");
            return;
        }
        
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            long fileSize = file.length();
            
            // Archivos pequeños por TCP, videos/archivos grandes por UDP
            if (!esVideo && fileSize < 1024 * 1024 * 10) { // < 10MB
                enviarArchivoTCP(file);
            } else {
                enviarArchivoUDP(file, esVideo);
            }
        }
    }

    private void enviarArchivoTCP(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            tcpOut.writeUTF("FILE");
            tcpOut.writeUTF(currentReceiver);
            tcpOut.writeUTF(file.getName());
            tcpOut.writeLong(file.length());
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                tcpOut.write(buffer, 0, bytesRead);
            }
            log("Archivo enviado por TCP: " + file.getName());
        } catch (IOException e) {
            log("Error enviando archivo: " + e.getMessage());
        }
    }

    private void enviarArchivoUDP(File file, boolean esVideo) {
        try {
            long fileSize = file.length();
            String fileName = file.getName();
            int totalPackets = (int) Math.ceil((double) fileSize / (UDP_PACKET_SIZE - UDP_HEADER_SIZE));
            
            log("Enviando " + fileName + " (" + totalPackets + " paquetes UDP)");
            
            // Leer el archivo completo
            byte[] fileData = Files.readAllBytes(file.toPath());
            
            for (int packetNumber = 0; packetNumber < totalPackets; packetNumber++) {
                int offset = packetNumber * (UDP_PACKET_SIZE - UDP_HEADER_SIZE);
                int length = Math.min(UDP_PACKET_SIZE - UDP_HEADER_SIZE, fileData.length - offset);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                
                // Escribir encabezado
                dos.writeUTF(esVideo ? "VIDEO" : "FILE");
                dos.writeUTF(currentReceiver);
                dos.writeUTF(username);
                dos.writeUTF(fileName);
                dos.writeLong(fileSize);
                dos.writeInt(totalPackets);
                dos.writeInt(packetNumber);
                dos.writeInt(length);
                
                // Escribir datos del paquete
                dos.write(fileData, offset, length);
                dos.flush();
                
                byte[] packetData = baos.toByteArray();
                
                DatagramPacket packet = new DatagramPacket(
                    packetData, packetData.length,
                    tcpSocket.getInetAddress(), tcpSocket.getPort() + 1
                );
                
                udpSocket.send(packet);
                
                // Pequeña pausa para no saturar la red
                Thread.sleep(1);
            }
            
            log("Archivo enviado por UDP: " + fileName);
            
        } catch (IOException | InterruptedException e) {
            log("Error enviando video: " + e.getMessage());
        }
    }

    private void recibirArchivoTCP(String remitente, String fileName, long fileSize) {
        try {
            String downloadFolder = Config.get("downloadFolderCliente");
            Path downloadDir = Paths.get(downloadFolder);
            
            // Crear directorio si no existe
            if (!Files.exists(downloadDir)) {
                Files.createDirectories(downloadDir);
            }
            
            // Verificar permisos de escritura
            if (!Files.isWritable(downloadDir)) {
                log("Error: No hay permisos de escritura en " + downloadDir);
                return;
            }
            
            Path destPath = downloadDir.resolve(fileName);
            
            // Manejar nombres duplicados
            int counter = 1;
            String originalName = fileName;
            while (Files.exists(destPath)) {
                int dotIndex = originalName.lastIndexOf('.');
                String name = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
                String ext = dotIndex > 0 ? originalName.substring(dotIndex) : "";
                fileName = name + "_" + counter + ext;
                destPath = downloadDir.resolve(fileName);
                counter++;
            }

            // Recibir el archivo
            try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                byte[] buffer = new byte[4096];
                long remaining = fileSize;
                while (remaining > 0) {
                    int read = tcpIn.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        log("Error: Conexión interrumpida durante recepción");
                        break;
                    }
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            // Verificar que el archivo se creó correctamente
            File archivoCreado = destPath.toFile();
            if (archivoCreado.exists() && archivoCreado.length() > 0) {
                ArchivoRecibido archivo = new ArchivoRecibido(remitente, fileName, 
                    destPath.toString(), fileSize, false);
                
                archivosRecibidos.computeIfAbsent(remitente, k -> new ArrayList<>()).add(archivo);
                
                log("Archivo recibido correctamente: " + fileName);
                mostrarNotificacionArchivo(remitente, fileName, fileSize, destPath.toString(), false);
            } else {
                log("Error: El archivo no se creó correctamente");
            }
            
        } catch (IOException e) {
            log("Error recibiendo archivo de " + remitente + ": " + e.getMessage());
        }
    }
    
    private void manejarNotificacionUDP(String remitente, String fileName, long fileSize, String filePath) {
        // El archivo ya fue guardado por el servidor, solo registrar la información
        ArchivoRecibido archivo = new ArchivoRecibido(remitente, fileName, filePath, fileSize, true);
        archivosRecibidos.computeIfAbsent(remitente, k -> new ArrayList<>()).add(archivo);
        
        mostrarNotificacionArchivo(remitente, fileName, fileSize, filePath, true);
    }
    
    private void mostrarNotificacionArchivo(String remitente, String fileName, long fileSize, 
                                         String filePath, boolean esUDP) {
        SwingUtilities.invokeLater(() -> {
            String tipo = esUDP ? "Video/Archivo" : "Archivo";
            String tamañoFormateado = formatSize(fileSize);
            
            chatArea.append("[" + tipo + " de " + remitente + "] " + fileName + 
                           " (" + tamañoFormateado + ")\n");
            
            // Crear botón de descarga en la interfaz
            JPanel panelNotificacion = new JPanel(new BorderLayout());
            JLabel label = new JLabel("📎 " + tipo + " recibido de " + remitente + ": " + fileName);
            JButton btnAbrir = new JButton("Abrir");
            
            btnAbrir.addActionListener(e -> {
                try {
                    File file = new File(filePath);
                    if (file.exists()) {
                        Desktop.getDesktop().open(file);
                        log("Archivo abierto: " + fileName);
                    } else {
                        JOptionPane.showMessageDialog(Cliente.this, 
                            "El archivo no existe en la ruta: " + filePath,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(Cliente.this, 
                        "No se pudo abrir el archivo: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            
            panelNotificacion.add(label, BorderLayout.CENTER);
            panelNotificacion.add(btnAbrir, BorderLayout.EAST);
            panelNotificacion.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            
            // Agregar al chat
            chatArea.append("[ARCHIVO DISPONIBLE] " + fileName + "\n");
        });
    }
    
    private void mostrarArchivosRecibidos() {
        JDialog dialog = new JDialog(this, "Archivos Recibidos", true);
        dialog.setSize(800, 500);
        dialog.setLayout(new BorderLayout());
        dialog.setLocationRelativeTo(this);

        // Panel principal con lista y botones
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Lista de archivos
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(listModel);
        
        // Llenar la lista
        for (Map.Entry<String, List<ArchivoRecibido>> entry : archivosRecibidos.entrySet()) {
            for (ArchivoRecibido archivo : entry.getValue()) {
                String item = String.format("%s - %s (%s) - %s",
                    archivo.remitente,
                    archivo.nombre,
                    formatSize(archivo.tamaño),
                    new SimpleDateFormat("dd/MM/yy HH:mm").format(archivo.fecha));
                listModel.addElement(item);
            }
        }

        JScrollPane scrollPane = new JScrollPane(fileList);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Panel de botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton btnAbrir = new JButton("Abrir Archivo");
        btnAbrir.addActionListener(e -> {
            int selectedIndex = fileList.getSelectedIndex();
            if (selectedIndex == -1) {
                JOptionPane.showMessageDialog(dialog, "Selecciona un archivo primero");
                return;
            }
            
            String selectedItem = listModel.getElementAt(selectedIndex);
            abrirArchivoSeleccionado(selectedItem, dialog);
        });
        
        JButton btnAbrirCarpeta = new JButton("Abrir Carpeta");
        btnAbrirCarpeta.addActionListener(e -> {
            try {
                String downloadFolder = Config.get("downloadFolderCliente");
                Desktop.getDesktop().open(new File(downloadFolder));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, 
                    "Error abriendo carpeta: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());

        buttonPanel.add(btnAbrir);
        buttonPanel.add(btnAbrirCarpeta);
        buttonPanel.add(btnCerrar);
        
        // Agregar listener de doble clic a la lista
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = fileList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedItem = listModel.getElementAt(index);
                        abrirArchivoSeleccionado(selectedItem, dialog);
                    }
                }
            }
        });

        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void abrirArchivoSeleccionado(String selectedItem, JDialog parentDialog) {
        // Buscar el archivo correspondiente al ítem seleccionado
        for (Map.Entry<String, List<ArchivoRecibido>> entry : archivosRecibidos.entrySet()) {
            for (ArchivoRecibido archivo : entry.getValue()) {
                String itemPattern = String.format("%s - %s (%s) - ",
                    archivo.remitente,
                    archivo.nombre,
                    formatSize(archivo.tamaño));
                
                if (selectedItem.startsWith(itemPattern)) {
                    File file = new File(archivo.ruta);
                    if (file.exists()) {
                        try {
                            Desktop.getDesktop().open(file);
                            log("Archivo abierto: " + archivo.nombre);
                            parentDialog.dispose();
                        } catch (IOException ex) {
                            JOptionPane.showMessageDialog(parentDialog,
                                "No se pudo abrir el archivo: " + ex.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(parentDialog,
                            "El archivo no existe en: " + archivo.ruta,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
            }
        }
        JOptionPane.showMessageDialog(parentDialog,
            "No se pudo encontrar el archivo seleccionado",
            "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void verificarArchivosRecibidos() {
        System.out.println("=== VERIFICACIÓN DE ARCHIVOS ===");
        System.out.println("Total de remitentes: " + archivosRecibidos.size());
        
        for (Map.Entry<String, List<ArchivoRecibido>> entry : archivosRecibidos.entrySet()) {
            System.out.println("Remitente: " + entry.getKey());
            System.out.println("Archivos: " + entry.getValue().size());
            
            for (ArchivoRecibido archivo : entry.getValue()) {
                File file = new File(archivo.ruta);
                System.out.println("  - " + archivo.nombre);
                System.out.println("    Ruta: " + archivo.ruta);
                System.out.println("    Existe: " + file.exists());
                System.out.println("    Tamaño: " + file.length() + " bytes");
                System.out.println("    Esperado: " + archivo.tamaño + " bytes");
            }
        }
        System.out.println("================================");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }

    private void log(String msg) {
        chatArea.append("[Sistema] " + msg + "\n");
    }

    private void habilitarControles(boolean estado) {
        enviarBtn.setEnabled(estado);
        enviarFileBtn.setEnabled(estado);
        enviarVideoBtn.setEnabled(estado);
        desconectarBtn.setEnabled(estado);
        conectarBtn.setEnabled(!estado);
        verArchivosBtn.setEnabled(estado);
    }

    private void desconectar() {
        try {
            if (tcpSocket != null) tcpSocket.close();
            if (udpSocket != null) udpSocket.close();
            habilitarControles(false);
            log("Desconectado");
        } catch (IOException e) {
            log("Error desconectando: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Cliente cliente = new Cliente();
            cliente.setVisible(true);
        });
    }
}