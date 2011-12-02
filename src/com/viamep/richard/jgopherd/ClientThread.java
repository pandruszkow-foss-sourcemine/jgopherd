package com.viamep.richard.jgopherd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;

public class ClientThread extends Thread {
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private String sport;
	private String rsport;
	private int scode;
	
	public ClientThread(Socket sock) {
		sport = Main.props.getPropertyString("name","127.0.0.1")+'\t'+Main.props.getPropertyInt("port",70);
		rsport = '\t'+"/"+'\t'+sport;
		socket = sock;
		try {
			sock.setSoTimeout(Main.props.getPropertyInt("timeout",15));
		} catch (Throwable e1) {
			// do nothing
		}
		try {
			out = new PrintWriter(sock.getOutputStream(),true);
		} catch (Throwable e) {
			Main.log.warning("Unable to create output stream: "+e.getMessage());
			return;
		}
		try {
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		} catch (Throwable e) {
			Main.log.warning("Unable to create input stream: "+e.getMessage());
			return;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void run() {
		InetSocketAddress addr = (InetSocketAddress)socket.getRemoteSocketAddress();
		String source = addr.getHostName()+":"+addr.getPort();
		String line = "";
		String fline = "";
		scode = 500;
		boolean log = true;
		boolean http = false;
		String httpreq = "";
		char httpkind = '1';
		boolean nomole = false;
		while (true) {
			try {
				line = in.readLine().replaceAll("\r","").replaceAll("\n","").replaceAll("\\.\\.","");
				//line = in.readLine();
			} catch (SocketTimeoutException e) {
				/*try {
					Main.log.warning("Connection from "+source+" timed out ("+socket.getSoTimeout()+" seconds)");
				} catch (Throwable e1) {
					Main.log.warning("Connection from "+source+" timed out");
				}*/
				continue;
			} catch (Throwable e) {
				continue;
			}
			if (!line.startsWith("/")) line = "/"+line;
			fline = line;
			String[] linex = line.split("\\?");
			try {
				line = linex[0];
			} catch (Throwable e) {
				line = "/";
			}
			String params;
			try {
				params = linex[1];
			} catch (Throwable e) {
				params = "";
			}
			if (line.startsWith("!nomole!")) {
				nomole = true;
				line = line.substring(5);
			}
				if (line.indexOf("$") != -1) {
					scode = 400;
					String[] sa = {"Your gopher client attempted to do a Gopher+ request. Gopher+ is not supported by this server."};
					MakeError("I do not do Gopher+!",sa);
					break;
				}
				File f = new File(Main.props.getPropertyString("root","gopherdocs")+line);
				if (line.startsWith("/GET ") || line.startsWith("/POST ")) {
					log = false;
					http = true;
					try {
						httpreq = line.split(" ")[1].split("/")[2];
					} catch (Throwable e) {
						httpreq = "/";
					}
					try {
						httpkind = line.split(" ")[1].charAt(1);
					} catch (Throwable e) {
						httpkind = '1';
					}
					if (!httpreq.startsWith("/")) httpreq = "/"+httpreq;
				} else if (http && (line.equalsIgnoreCase("/"))) {
					scode = 200;
					if (httpkind == '@') {
						out.println("HTTP/1.1 200 OK");
						out.println("Content-Type: image/png");
						out.println("");
						HTTPIcons hi = new HTTPIcons();
						try {
							out.print(hi.icons.get(httpreq.replaceFirst("\\.png","")));
						} catch (Throwable e) {
							out.print(hi.icons.get("generic"));
						}
						out.flush();
						break;
					}
					ArrayList<GopherEntry> al = MakeEntries(httpreq);
					boolean haserror = false;
					for (GopherEntry ge : al) {
						if (ge.kind == '3') haserror = true;
					}
					out.println("HTTP/1.1 "+(haserror ? "404 Not Found" : "200 OK"));
					if (httpkind == '1') {
						out.println("Content-Type: text/html");
						out.println("");
						out.println("<html><head><title>Gopher: "+Util.HTMLEscape(httpreq)+"</title></head><body>");
						out.println("<h2>Gopher: "+Util.HTMLEscape(httpreq)+"</h2><hr>");
						out.println("<table border=\"0\"><tbody>");
						for (GopherEntry ge : al) {
							if (ge.kind == 'i') {
								out.println("<tr><td>&nbsp;</td><td><pre>"+ge.title+"</pre></td></tr>");
							} else {
								String icon;
								if ((ge.kind == 'h') && ge.destination.startsWith("URL:")) {
									icon = "hurl";
								} else {
									icon = ""+ge.kind;
								}
								out.println("<tr><td><img src=\"/@/"+icon+".png\"></td><td><pre><a href=\""+((ge.host == Main.props.getPropertyString("name","127.0.0.1")) ? "http" : "gopher")+"://"+ge.host+":"+ge.port+"/"+ge.kind+"/"+ge.destination+"\">"+ge.title+"</a></pre></td></tr>");
							}
						}
						out.println("</tbody></table>");
						out.println("<hr><i>Generated by jgopherd v"+Main.version+" on "+Main.props.getPropertyString("name","127.0.0.1")+"</i>");
						out.println("</body></html>");
					} else {
						BufferedReader br;
						try {
							br = new BufferedReader(new FileReader(httpreq));
						} catch (Throwable e1) {
							break;
						}
						while (true) {
							try {
								out.print(br.readLine());
							} catch (Throwable e) {
								break;
							}
						}
					}
					break;
				} else if (http) {
					continue;
				} else if (f.isDirectory() && new File(f.getAbsoluteFile()+"/gophermap").exists()) {
					scode = 200;
					for (GopherEntry ge : MakeEntries(line)) {
						out.println(ge.GetAsRaw());
					}
				} else if (Util.IsExecutable(f) && !nomole) {
					scode = 200;
					ArrayList<String> envvars = new ArrayList<String>();
					envvars.add("REMOTE_HOST="+addr.getHostName());
					envvars.add("REMOTE_ADDR="+addr.getHostString());
					envvars.add("REMOTE_PORT="+addr.getPort());
					envvars.add("SERVER_HOST="+Main.props.getPropertyString("name","127.0.0.1"));
					envvars.add("SERVER_PORT="+Main.props.getPropertyInt("port",70));
					envvars.add("SELECTOR="+fline);
					envvars.add("REQUEST="+line);
					Process prc;
					try {
						String[] sa = {f.getAbsolutePath()};
						prc = Runtime.getRuntime().exec(Util.ConcatArrays(sa,params.split(" ")),(String[])envvars.toArray(new String[envvars.size()]),f.getParentFile());
					} catch (Throwable e) {
						scode = 500;
						MakeError("Error while trying to execute mole",e);
						break;
					}
					BufferedReader pos = new BufferedReader(new InputStreamReader(prc.getInputStream()));
					String ln;
					while (true) {
						try {
							ln = pos.readLine();
						} catch (Throwable e) {
							continue;
						}
						out.println(ln);
						try {
							prc.exitValue();
							break;
						} catch (Throwable e) {
							continue;
						}
					}
				} else if (f.getName().endsWith(".class") && f.exists() && !nomole) {
					scode = 500;
					URL url;
					try {
						url = new URL("file://"+f.getParent());
					} catch (Throwable e) {
						MakeError("Error while loading j-mole: Invalid file",e);
						break;
					}
					URL[] urla = {url};
					URLClassLoader ucl = new URLClassLoader(urla);
					Class<JMole> cls;
					try {
						cls = (Class<JMole>)ucl.loadClass(f.getName().substring(0,f.getName().length()-6));
					} catch (Throwable e) {
						MakeError("Error while loading j-mole: Invalid class",e);
						break;
					}
					HashMap<String,String> envmap = new HashMap<String,String>();
					envmap.put("REMOTE_HOST",addr.getHostString());
					envmap.put("REMOTE_ADDR",addr.getHostName());
					envmap.put("REMOTE_PORT",""+addr.getPort());
					envmap.put("SERVER_HOST",Main.props.getPropertyString("name","127.0.0.1"));
					envmap.put("SERVER_PORT",""+Main.props.getPropertyInt("port",70));
					envmap.put("SELECTOR",fline);
					envmap.put("REQUEST",line);
					ArrayList<GopherEntry> entries;
					try {
						entries = cls.newInstance().run(envmap);
					} catch (Throwable e) {
						MakeError("Error while executing j-mole",e);
						break;
					}
					scode = 200;
					for (GopherEntry ge : entries) {
						out.println(ge.GetAsRaw());
					}
				} else {
					scode = 404;
					if (line.equalsIgnoreCase("/")) {
						out.println("3Welcome to jgopherd!"+rsport);
						out.println("i"+rsport);
						out.println("iThis is a new installation of jgopherd on "+Main.props.getPropertyString("name","127.0.0.1")+"."+rsport);
						out.println("iThere is currently no content to be served from this installation."+rsport);
						out.println("i"+rsport);
						out.println("iAdministrator: To start using the server, place a gophermap file"+rsport);
						out.println("ion the gopherdocs directory (or the directory you have configured)"+rsport);
						out.println("iformatted as a Bucktooth gophermap file. After the file is found,"+rsport);
						out.println("ithis message will disappear and the gophermap will be useds."+rsport);
						out.println("i"+rsport);
						out.println("iGenerated by jgopherd v"+Main.version+" on "+Main.props.getPropertyString("name","127.0.0.1")+rsport);
					} else {
						String[] sa = {"The specified resource was not found on this server."};
						MakeError("Resource not found",sa);
					}
				}
			}
		if (log) Main.log.finest(source+" "+scode+" "+line);
		if (!http) out.println(".");
		try {
			socket.close();
		} catch (IOException e) {
			// do nothing
		}
	}
	
	private ArrayList<GopherEntry> MakeError(String error, String[] details) {
		ArrayList<GopherEntry> al = new ArrayList<GopherEntry>();
		try {
			al.add(new GopherEntry('3',error));
			al.add(new GopherEntry('i',""));
			for (String det : details) {
				al.add(new GopherEntry('i',det));
			}
		} catch (Throwable e) {
			// do nothing
		}
		al.add(new GopherEntry('i',""));
		al.add(new GopherEntry('i',"Generated by jgopherd v"+Main.version+" on "+Main.props.getPropertyString("name","127.0.0.1")));
		return al;
	}
	
	private ArrayList<GopherEntry> MakeError(String error, Throwable e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String[] sst = {"=== BEGIN STACK TRACE ==="};
		String[] est = {"=== END STACK TRACE ==="};
		return MakeError(error,Util.ConcatArrays(sst,sw.toString().split("\n"),est));
	}
	
	private ArrayList<GopherEntry> MakeEntries(String line) {
		FileInputStream fis;
		try {
			fis = new FileInputStream(Main.props.getPropertyString("root","gopherdocs")+line+"/gophermap");
		} catch (Throwable e) {
			System.out.println(e);
			ArrayList<GopherEntry> al = new ArrayList<GopherEntry>();
			al.add(new GopherEntry('i',"Directory listing for "+line));
			al.add(new GopherEntry('i',""));
			File f1;
			for (String fn : new File(Main.props.getPropertyString("root","gopherdocs")+line).list()) {
				if (!fn.equalsIgnoreCase("gophermap")&&!fn.equalsIgnoreCase("gophertag")) {
					f1 = new File(fn);
					al.add(new GopherEntry(Util.GetType(f1.getAbsolutePath()),f1.getName(),line+"/"+f1.getName()));
				}
			}
			al.add(new GopherEntry('i',""));
			al.add(new GopherEntry('i',"Generated by jgopherd v"+Main.version+" on "+Main.props.getPropertyString("name","127.0.0.1")));
			return al;
		}
		return new BuckGophermap().parse(line,fis);
	}
}