package by.goncharov;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;

public class Server {
    static final String OkStatusCode= "HTTP/1.1 200 OK";
    static final String fileNotFoundStatusCode= "HTTP/1.1 404 FILE NOT FOUND";
    static final String fileOverwrittenStatusCode= "HTTP/1.1 201 FILE OVER-WRITTEN";
    static final String newFileCreatedStatusCode= "HTTP/1.1 202 NEW FILE CREATED";
    static final String connectionAlive= "Connection: keep-alive";
    static final String server= "Server: httpfs/1.0.0";
    static final String date= "Date: ";
    static final String accessControlAllowOrigin = "Access-Control-Allow-Origin: *";
    static final String accessControlAllowCredentials = "Access-Control-Allow-Credentials: true";
    static final String via ="Via : 1.1 vegur";
    static boolean debug=false;

    public static void main(String[] args) throws IOException {

        String request;
        ArrayList<String> requestList = new ArrayList<>();
        int port=8888;

        String dir = System.getProperty("user.dir");

        System.out.print(">");
        Scanner sc=new Scanner(System.in);
        request = sc.nextLine();
        if(request.isEmpty() || request.length()==0){
            System.out.println("Invalid Command");
        }

        ServerSocket listenSocket = new ServerSocket(port);
        if(debug)
            System.out.println("Server up on port number : "+port);

        File currentFolder = new File(dir);
        while(true){

            Socket server = listenSocket.accept();
            if(debug)
                System.out.println("Connected to client");
            DataInputStream in = new DataInputStream(server.getInputStream());
            DataOutputStream out = new DataOutputStream(server.getOutputStream());
            request = in.readUTF();

            String[] clientRequestArray = request.split(" ");
            ArrayList<String> clientRequestList = new ArrayList<>();
            for (int i = 0; i < clientRequestArray.length; i++) {
                clientRequestList.add(clientRequestArray[i]);
            }

            String clientType=in.readUTF();
            String method = in.readUTF();
            String url = in.readUTF();
            String host = in.readUTF();
            DateFormat dateFormat=new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Date date=new Date();
            String currentDateAndTime = dateFormat.format(date);
            String responseHeaders = OkStatusCode +"\n"+connectionAlive+"\n"+Server.server
                    +"\n"+Server.date+currentDateAndTime+"\n"+
                    accessControlAllowOrigin+"\n"+accessControlAllowCredentials+"\n"+via+"\n";

            if(clientType.equals("httpc")){
                if(debug)
                    System.out.println("Processing the httpc request");
                String parameters;
                String[] paramArr = {};
                if(!(url.length()-1==url.indexOf("?"))){
                    System.out.println("inside");
                    parameters = url.substring(url.indexOf("?")+1);
                    paramArr = parameters.split("&");
                }
                String inlineData="";
                String fileData="";

                if(request.contains("-v")){
                    out.writeUTF(responseHeaders);
                }
                String body = "{\n";
                body = body+"\t\"args\":";
                body = body +"{";
                if(paramArr.length>0){
                    for(int i=0;i<paramArr.length;i++){
                        body = body + "\n\t    \""+paramArr[i].substring(0, paramArr[i].indexOf("="))
                                + "\": \""+paramArr[i].substring(paramArr[i].indexOf("=")+1)+"\"";
                        if(i!=paramArr.length-1){
                            body = body + ",";
                        }
                        else{
                            body = body + "\n";
                            body = body + "\t},\n";
                        }
                    }
                }
                else{
                    body = body +"},\n";
                }

                if(method.equals("POST")){
                    body = body + "\t\"data\": ";
                    if(request.contains("-d")) {
                        inlineData=in.readUTF();
                        body = body +"\""+inlineData+"\",\n";
                    }
                    else if(request.contains("-f")){
                        fileData=in.readUTF();
                        body = body +"\""+fileData+"\",\n";
                    }
                    else{
                        body = body + "\"\",\n";
                    }
                    body = body + "\t\"files\": {},\n";
                    body = body + "\t\"form\": {},\n";
                }


                body = body + "\t\"headers\": {";
                if(request.contains("-h")){
                    int noOfHeaders = Integer.parseInt(in.readUTF());
                    for(int i=0;i<noOfHeaders;i++){
                        String header = in.readUTF();
                        String[] headerArr = header.split(":");
                        if(headerArr[0].equalsIgnoreCase("connection"))
                            continue;
                        body = body + "\n\t\t\""+headerArr[0]+"\": \""+headerArr[1].trim()+"\",";
                    }
                }
                if(request.contains("-d")){
                    body = body + "\n\t\t\"Content-Length\": \""+inlineData.length()+"\",";
                }
                else if(request.contains("-f")){
                    body = body + "\n\t\t\"Content-Length\": \""+fileData.length()+"\",";
                }
                body = body + "\n\t\t\"Connection\": \"close\",\n";
                body = body + "\t\t\"Host\": \""+host+"\"\n";
                body = body +  "\t},\n";

                if(method.equals("POST")){
                    body = body + "\t\"json\": ";
                    if(request.contains("-d")){
                        body = body + "{\n\t\t "+inlineData.substring(1, inlineData.length()-1)+"\n\t},\n";
                    }
                    else{
                        body = body + "null,\n";
                    }
                }
                body = body +  "\t\"origin\": \""+InetAddress.getLocalHost().getHostAddress()+"\",\n";
                body = body +  "\t\"url\": \""+url+"\"\n";
                body = body +  	"}";

                out.writeUTF(body);


            }
            else if(clientType.equals("httpfs")){
                if(debug)
                    System.out.println("Processing the httpfs request");
                String status="";
                String body = "{\n";
                body = body+"\t\"args\":";
                body = body +"{},\n";
                body = body + "\t\"headers\": {";

                if(!method.endsWith("/") && method.contains("get/") && request.contains("Content-Disposition:attachment")){
                    body = body + "\n\t\t\"Content-Disposition\": \"attachment\",";
                }
                else if(!method.endsWith("/") && method.contains("get/") && request.contains("Content-Disposition:inline")){
                    body = body + "\n\t\t\"Content-Disposition\": \"inline\",";
                }
                body = body + "\n\t\t\"Connection\": \"close\",\n";
                body = body + "\t\t\"Host\": \""+host+"\"\n";
                body = body +  "\t},\n";

                if(method.equalsIgnoreCase("get/")){

                    body = body + "\t\"files\": { ";
                    ArrayList<String> files = listDirectoryFiles(currentFolder);
                    for(int i=0;i<files.size();i++){
                        if(i!=files.size()-1){
                            body = body + files.get(i)+" , ";
                        }
                        else{
                            body = body + files.get(i)+" },\n";
                        }
                    }
                }

                else if(!method.endsWith("/") && method.contains("get/")){

                    String response="";
                    String requestedFileName = method.split("/")[1];
                    ArrayList<String> files = listDirectoryFiles(currentFolder);

                    if(request.contains("Content-Type")){
                        String fileType = clientRequestList.get(clientRequestList.indexOf("-h")+1).split(":")[1];
                        requestedFileName=requestedFileName+"."+fileType;
                    }

                    if(!files.contains(requestedFileName)){
                        responseHeaders=fileNotFoundStatusCode +"\n"+connectionAlive+"\n"+Server.server+"\n"+Server.date+currentDateAndTime+
                                "\n"+accessControlAllowOrigin+"\n"+accessControlAllowCredentials+"\n"+via+"\n";
                        out.writeUTF("404");
                    }
                    else{
                        File file = new File(dir + "/" + requestedFileName);
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String st;
                        while((st = br.readLine())!=null){
                            response = response + st;
                        }
                        if(request.contains("Content-Disposition:attachment")){
                            out.writeUTF("203");
                            out.writeUTF(response);
                            out.writeUTF(requestedFileName);
                        }
                        else{
                            out.writeUTF("203");
                            body = body + "\t\"data\": \""+response+"\",\n";
                        }

                    }


                }

                else if(!method.endsWith("/") && method.contains("post/")){

                    String fileName = method.split("/")[1];
                    File file = new File(fileName);
                    ArrayList<String> files = listDirectoryFiles(currentFolder);
                    if (files.contains(fileName)) {
                        out.writeUTF(fileName+" exists. Do you want to overwrite it. Press 'Y' for yes or 'N' for No.");
                        String ans = in.readUTF().trim();
                        if(ans.equalsIgnoreCase("Y")){
                            synchronized (file) {
                                file.delete();
                                file = new File(dir+"/"+fileName);
                                file.createNewFile();
                                FileWriter fw = new FileWriter(file);
                                fw.write(request.substring(request.indexOf("-d")+3));
                                fw.close();
                            }
                            responseHeaders=fileOverwrittenStatusCode +"\n"+connectionAlive+"\n"+Server.server+"\n"+Server.date+currentDateAndTime+
                                    "\n"+accessControlAllowOrigin+"\n"+accessControlAllowCredentials+"\n"+via+"\n";
                        }
                    }
                    else{
                        out.writeUTF(fileName+" does not exist. Do you want to create a new file? Press 'Y' for yes or 'N' for No." );
                        if(in.readUTF().equalsIgnoreCase("Y")){
                            file = new File(dir+"/"+fileName);
                            synchronized (file) {
                                file.createNewFile();
                                FileWriter fw = new FileWriter(file);
                                BufferedWriter bw = new BufferedWriter(fw);
                                PrintWriter pw = new PrintWriter(bw);

                                pw.print(request.substring(request.indexOf("-d")+3));
                                pw.flush();
                                pw.close();
                            }
                            responseHeaders=newFileCreatedStatusCode +"\n"+connectionAlive+"\n"+Server.server+"\n"+Server.date+currentDateAndTime+
                                    "\n"+accessControlAllowOrigin+"\n"+accessControlAllowCredentials+"\n"+via+"\n";
                        }
                    }
                }
                body = body +  "\t\"origin\": \""+InetAddress.getLocalHost().getHostAddress()+"\",\n";
                body = body +  "\t\"url\": \""+url+"\"\n";
                body = body +  	"}";

                if(debug)
                    System.out.println("Sending the response");
                out.writeUTF(responseHeaders);
                out.writeUTF(body);
            }
        }
    }

    private static ArrayList<String> listDirectoryFiles(File currentFolder){
        ArrayList<String> files = new ArrayList<>();
        for(File file : currentFolder.listFiles()){
            if(!file.isDirectory()){
                files.add(file.getName());
            }
        }
        return files;
    }
}
