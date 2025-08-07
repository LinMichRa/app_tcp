package org.vinni.servidor.gui;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrincipalSrv extends javax.swing.JFrame {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private DataInputStream dataIn;
    private DataOutputStream dataOut;
    private String downloadFolder = "server_downloads";
    private String uploadFolder = "server_uploads";
    private long maxFileSize = 100 * 1024 * 1024; // 100MB límite

    // Componentes de la GUI
    private javax.swing.JButton bIniciar;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextArea mensajesTxt;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField puertoTxt;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JButton bDetener;
    private javax.swing.JButton bSeleccionarUpload;
    private javax.swing.JButton bSeleccionarDownload;

    public PrincipalSrv() {
        initComponents();
        crearDirectorios();
    }

    private void initComponents() {
        this.setTitle("Servidor TCP");

        bIniciar = new javax.swing.JButton();
        bDetener = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        mensajesTxt = new javax.swing.JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();
        puertoTxt = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        bSeleccionarUpload = new javax.swing.JButton();
        bSeleccionarDownload = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(null);

        bIniciar.setText("INICIAR SERVIDOR");
        bIniciar.addActionListener(e -> iniciarServidor());
        getContentPane().add(bIniciar);
        bIniciar.setBounds(50, 90, 150, 40);

        bDetener.setText("DETENER SERVIDOR");
        bDetener.setEnabled(false);
        bDetener.addActionListener(e -> detenerServidor());
        getContentPane().add(bDetener);
        bDetener.setBounds(220, 90, 150, 40);

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14));
        jLabel1.setText("SERVIDOR TCP");
        getContentPane().add(jLabel1);
        jLabel1.setBounds(150, 10, 160, 17);

        mensajesTxt.setColumns(25);
        mensajesTxt.setRows(5);
        jScrollPane1.setViewportView(mensajesTxt);
        getContentPane().add(jScrollPane1);
        jScrollPane1.setBounds(20, 200, 450, 200);

        puertoTxt.setText("12345");
        getContentPane().add(puertoTxt);
        puertoTxt.setBounds(150, 50, 100, 30);

        jLabel2.setText("Puerto:");
        getContentPane().add(jLabel2);
        jLabel2.setBounds(100, 50, 50, 30);

        bSeleccionarUpload.setText("Seleccionar Carpeta Upload");
        bSeleccionarUpload.addActionListener(e -> seleccionarCarpetaUpload());
        getContentPane().add(bSeleccionarUpload);
        bSeleccionarUpload.setBounds(50, 140, 200, 30);

        bSeleccionarDownload.setText("Seleccionar Carpeta Download");
        bSeleccionarDownload.addActionListener(e -> seleccionarCarpetaDownload());
        getContentPane().add(bSeleccionarDownload);
        bSeleccionarDownload.setBounds(260, 140, 200, 30);

        setSize(new java.awt.Dimension(500, 450));
        setLocationRelativeTo(null);
    }

    private void crearDirectorios() {
        try {
            Files.createDirectories(Paths.get(downloadFolder));
            Files.createDirectories(Paths.get(uploadFolder));
            mensajesTxt.append("Directorios creados:\n- " + downloadFolder + "\n- " + uploadFolder + "\n");
        } catch (IOException e) {
            mensajesTxt.append("Error creando directorios: " + e.getMessage() + "\n");
        }
    }

    private void seleccionarCarpetaUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            uploadFolder = chooser.getSelectedFile().getAbsolutePath();
            mensajesTxt.append("Carpeta upload seleccionada: " + uploadFolder + "\n");
        }
    }

    private void seleccionarCarpetaDownload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            downloadFolder = chooser.getSelectedFile().getAbsolutePath();
            mensajesTxt.append("Carpeta download seleccionada: " + downloadFolder + "\n");
        }
    }

    private void iniciarServidor() {
        int port;
        try {
            port = Integer.parseInt(puertoTxt.getText());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Puerto inválido");
            return;
        }

        bIniciar.setEnabled(false);
        bDetener.setEnabled(true);
        puertoTxt.setEnabled(false);

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                mensajesTxt.append("Servidor TCP iniciado en puerto: " + port + "\n");
                mensajesTxt.append("Esperando conexiones...\n");

                while (!serverSocket.isClosed()) {
                    clientSocket = serverSocket.accept();
                    mensajesTxt.append("Cliente conectado: " + clientSocket.getInetAddress() + "\n");

                    // Configurar streams
                    out = new PrintWriter(clientSocket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    dataIn = new DataInputStream(clientSocket.getInputStream());
                    dataOut = new DataOutputStream(clientSocket.getOutputStream());

                    // Manejar conexión en un hilo separado
                    new Thread(this::manejarConexion).start();
                }
            } catch (IOException e) {
                if (!e.getMessage().equals("socket closed")) {
                    mensajesTxt.append("Error en el servidor: " + e.getMessage() + "\n");
                }
            }
        }).start();
    }

    private void manejarConexion() {
        try {
            String comando;
            while ((comando = in.readLine()) != null) {
                switch (comando) {
                    case "SUBIR_ARCHIVO":
                        recibirArchivo();
                        break;
                    case "DESCARGAR_ARCHIVO":
                        enviarArchivo();
                        break;
                    case "MENSAJE":
                        String mensaje = in.readLine();
                        mensajesTxt.append("Cliente dice: " + mensaje + "\n");
                        out.println("Mensaje recibido en el servidor");
                        break;
                    default:
                        out.println("Comando no reconocido: " + comando);
                }
            }
        } catch (IOException e) {
            mensajesTxt.append("Error en la conexión con el cliente: " + e.getMessage() + "\n");
        } finally {
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                mensajesTxt.append("Error cerrando socket: " + e.getMessage() + "\n");
            }
        }
    }

    private void recibirArchivo() throws IOException {
        // Recibir información del archivo
        String nombreArchivo = in.readLine();
        long tamanoArchivo = Long.parseLong(in.readLine());
        
        if (tamanoArchivo > maxFileSize) {
            out.println("ERROR: El archivo excede el tamaño máximo permitido (" + maxFileSize / (1024 * 1024) + "MB)");
            return;
        }

        out.println("OK"); // Confirmar que se puede recibir el archivo

        // Crear el archivo local
        Path path = Paths.get(downloadFolder, nombreArchivo);
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            byte[] buffer = new byte[4096];
            int leidos;
            long totalLeido = 0;
            
            while (totalLeido < tamanoArchivo && 
                  (leidos = dataIn.read(buffer, 0, (int) Math.min(buffer.length, tamanoArchivo - totalLeido))) != -1) {
                fos.write(buffer, 0, leidos);
                totalLeido += leidos;
            }
            
            mensajesTxt.append("Archivo recibido: " + nombreArchivo + " (" + tamanoArchivo + " bytes)\n");
            out.println("Archivo recibido correctamente en el servidor");
        } catch (IOException e) {
            out.println("ERROR: " + e.getMessage());
            mensajesTxt.append("Error recibiendo archivo: " + e.getMessage() + "\n");
            Files.deleteIfExists(path); // Intentar borrar el archivo incompleto
        }
    }

    private void enviarArchivo() throws IOException {
        // Seleccionar archivo para enviar
        JFileChooser fileChooser = new JFileChooser(uploadFolder);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            
            if (archivo.length() > maxFileSize) {
                out.println("ERROR: El archivo excede el tamaño máximo permitido (" + maxFileSize / (1024 * 1024) + "MB)");
                return;
            }
            
            out.println(archivo.getName()); // Enviar nombre del archivo
            out.println(archivo.length()); // Enviar tamaño del archivo
            
            // Esperar confirmación del cliente
            String respuesta = in.readLine();
            if (!"OK".equals(respuesta)) {
                mensajesTxt.append("Cliente rechazó el archivo: " + respuesta + "\n");
                return;
            }
            
            // Enviar el archivo
            try (FileInputStream fis = new FileInputStream(archivo)) {
                byte[] buffer = new byte[4096];
                int leidos;
                
                while ((leidos = fis.read(buffer)) > 0) {
                    dataOut.write(buffer, 0, leidos);
                }
                
                mensajesTxt.append("Archivo enviado: " + archivo.getName() + "\n");
            } catch (IOException e) {
                mensajesTxt.append("Error enviando archivo: " + e.getMessage() + "\n");
            }
        } else {
            out.println("CANCELADO");
        }
    }

    private void detenerServidor() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                mensajesTxt.append("Servidor detenido\n");
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            mensajesTxt.append("Error deteniendo servidor: " + e.getMessage() + "\n");
        }
        
        bIniciar.setEnabled(true);
        bDetener.setEnabled(false);
        puertoTxt.setEnabled(true);
    }

    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(() -> new PrincipalSrv().setVisible(true));
    }
}