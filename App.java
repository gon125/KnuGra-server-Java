package knugra.server.java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class App {
	
    public static void main(String[] args) {
    	Server.getInstance().start();
    	
    }
    
    private static class Server {
    	
    	private static Server instance = null;
    	private ServerSocket serverSocket = null;
    	private static final int PORT = 34567;
    	private ExecutorService threadPool = Executors.newFixedThreadPool(4);
    	private static final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Safari/605.1.15";
    	private static HashMap<String, Map<String, String>> loggedInUsers = new HashMap<>();
    	
    	
    	private Server() {
    		
    	}
    	public static Server getInstance() {
    		if (instance == null) {
    			instance = new Server();
    		}
    		return instance;
    	}
    	
    	public void start() {
    		
    		try {
				serverSocket = new ServerSocket();
				serverSocket.bind(new InetSocketAddress(PORT));
			} catch (IOException e) {
				e.printStackTrace();
			}
    		
    		while(true) {
    			try {
    				Socket socket = serverSocket.accept();
    				System.out.println("Socket Accepted:" + socket);
        			threadPool.execute(new HandleClientRunnable(socket));
    			} catch(IOException e) {
    				e.printStackTrace();
    			}
    		}
    		
    		
    		
    	}
    	
    	private class HandleClientRunnable implements Runnable {
    		
    		private Socket socket = null;

    		
    		public HandleClientRunnable(Socket socket) {
    			this.socket = socket;
    		}
    		
			@Override
			public void run() {
				try {
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
					String readString = in.readLine();
					ObjectMapper m = new ObjectMapper();
				
					Map<String, String> input = m.readValue(readString, new TypeReference<Map<String, String>>(){});
					Map<String, Object> output = new HashMap<String, Object>();
					
					final String request = input.get("requestType");
					final String id = input.get("id");
					final String major = input.get("major");
					
					if (request.equals("login")) {
						final String password = input.get("pwd");
						Result r = login(id, password, major);
						
						if (r.isSucceed) {
							output.put("login", "success");
						} else {
							output.put("login", "fail");
							output.put("errorCode", r.errorCode);
						}
			
					} else if (request.equals("getGradeInfo")) {
						Result r = getGradeInfo(id, major);
						output = r.output;
					} else if (request.equals("logout")) {
						logout();
						output.put("logout", "success");
					} else {
						// error
						output.put("errorCode", Error.Code.INVALID_REQUEST);
					}
					
					String jsonString = m.writeValueAsString(output);
					
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
					out.write(jsonString);
					out.newLine();
					out.flush();
					
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
			private void logout() {
				// TODO Auto-generated method stub
				
			}

			private Result getGradeInfo(String id, String major) {
				
				Result r = null;
									
				if (major.equals("abeek")) {
					r = abeekUpdate(id);
				} else if (major.equals("global")) {
					r = yesUpdate(id);
				} else {
					//error			
				}
				
				return r;
			}

			private Result yesUpdate(String id) {
				try {
					Connection.Response currentResponse;
					Map<String, String> currentCookies;
					Map<String, String> data = new HashMap<>();
					Map<String, Object> output = new HashMap<>();
					
					if (loggedInUsers.get(id) == null) {
						return new Result(false, Error.Code.USER_NOT_LOGGED_IN);
					}
					currentCookies = loggedInUsers.get(id);
					
					
					data.put("user.usr_id", id);
					data.put("user.user_div", "");
					data.put("user.stu_persnl_nbr", "");
					
					currentResponse = loginActionABEEK(null, data);
					currentCookies = currentResponse.cookies();
					
					currentResponse = mainActionABEEK(currentCookies);
					currentCookies = currentResponse.cookies();
					
					Document dc = currentResponse.parse();
					//System.out.println(dc.toString());
					
					Element e = dc.getElementById("myinfo");
					
					if(e == null) {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					} else {
						loggedInUsers.put(id, new HashMap<>(currentCookies));
						return new Result(true, 0);
					}
					
				} catch (IOException e) {
					if (id == "") {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					}
					e.printStackTrace();
				}
				return new Result(false, Error.Code.UNKNOWN);
			}

			private Result abeekUpdate(String id) {
				try {
					Connection.Response currentResponse;
					Map<String, String> currentCookies;
					Map<String, String> data = new HashMap<>();
					Map<String, Object> output = new HashMap<>();
					
					if (loggedInUsers.get(id) == null) {
						return new Result(false, Error.Code.USER_NOT_LOGGED_IN);
					}
					currentCookies = loggedInUsers.get(id);
					
					System.out.println(currentCookies.toString());
					
					currentResponse = listActionABEEK(currentCookies);
					System.out.println(currentResponse.body());
					
					Document dc = currentResponse.parse();
					
					
					
					//getGradeInfo
					Elements es = dc.getElementsByClass("info_table mb_30");
					Element table = es.get(0).selectFirst("table");
					System.out.println(table.toString());
					Map<String, String> getGradeInfo = new HashMap<String, String>();
					
					Elements rows = table.select("tr");
					
					Element row = rows.get(0);
					Elements names = row.select("th");
					Elements values = rows.get(2).select("td");
					
					for (int i = 0; i < names.size(); i++) {
						if (names.get(i).attr("colspan").equals("")) {
							getGradeInfo.put(names.get(i).text().replaceAll(" ", ""), values.get(i).text());
						} else {
							Elements subNames = rows.get(1).select("th");
							for (int j = 0; j < subNames.size(); j++) {
								getGradeInfo.put(subNames.get(j).text().strip(), values.get(i+j).text());
							}
						}
					}
					
					
					//completeSubjectList
					ArrayList<Map<String, String>> completeSubjectList = new ArrayList<>();
					table = dc.getElementsByClass("defaultTable_pop").get(0);
					rows = table.select("tr");
					String[] colName = {"교과목번호","개설학과","교과목명","교과구분","학점","학기","평점","재이수"};
					
					for (int i = 1; i < rows.size(); i++) {
						Elements cols = rows.get(i).select("td");
						Map<String, String> subject = new HashMap<>();
						subject.put(colName[0], cols.get(0).text());
						subject.put(colName[2], cols.get(2).text());
						//subject.put(colName[4], cols.get(4).text());
						completeSubjectList.add(subject);
					}
					
					output.clear();
					output.put("completeSubjectList", completeSubjectList);
					output.put("getGradeInfo", getGradeInfo);
					
					return new Result(true, 0, output);
					
				} catch (IOException e) {
					if (id == "") {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					}
					e.printStackTrace();
				}
				return new Result(false, Error.Code.UNKNOWN);
			}

			private Response listPgmCdesActionABEEK(Map<String, String> currentCookies) {
				Connection.Response response = null;
				final String URL ="http://abeek.knu.ac.kr/Keess/comm/support/login/listPgmCdes.action";
				final int TIMEOUT = 5000;
				final String accept = "text/javascript, text/html, application/xml, text/xml, */*";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate";
				final String host = "abeek.knu.ac.kr";
				final String origin = "http://abeek.knu.ac.kr";
				final String referer = "http://abeek.knu.ac.kr/Keess/kees/web/stue/stueStuRecEnq/list.action";
				final String connection = "keep-alive";
				final String xPrototypeVersion = "1.6.1";
				final String xRequestedWith = "XMLHttpRequest";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.header("X-Prototype-Version", xPrototypeVersion)
							.header("X-Requested-With", xRequestedWith)
							.ignoreContentType(true)
							.userAgent(userAgent)
							.cookies(currentCookies)
							.method(Connection.Method.POST)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Response listActionABEEK(Map<String, String> currentCookies) {
				Connection.Response response = null;
				final String URL ="http://abeek.knu.ac.kr/Keess/kees/web/stue/stueStuRecEnq/list.action";
				final int TIMEOUT = 5000;
				//final String contentType = "application/x-www-form-urlencoded";
				final String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate";
				final String host = "abeek.knu.ac.kr";
				//final String origin = "http://abeek.knu.ac.kr";
				final String referer = "http://abeek.knu.ac.kr/Keess/kees/web/stue/stueStuRecEnq/list.action";
				final String connection = "keep-alive";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							//.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.userAgent(userAgent)
							.cookies(currentCookies)
							.method(Connection.Method.GET)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Result login(final String id, final String password, final String major) {
				
				Result r = null;
				
				if (major.equals("abeek")) {
					r = abeekLogin(id, password);
				} else if (major.equals("global")) {
					r = yesLogin(id, password);
				} else {
					//error			
				}
				
				return r;
			}

			private Result abeekLogin(final String id, final String pwd) {
				
				try {
					Connection.Response currentResponse;
					Map<String, String> currentCookies;
					Map<String, String> data = new HashMap<>();
					
					data.put("user.usr_id", id);
					data.put("user.passwd", pwd);
					data.put("user.user_div", "");
					data.put("user.stu_persnl_nbr", "");
					
					currentResponse = loginActionABEEK(null, data);
					currentCookies = currentResponse.cookies();
					
					currentResponse = mainActionABEEK(currentCookies);
					
					Document dc = currentResponse.parse();
					//System.out.println(dc.toString());
					
					Element e = dc.getElementById("myinfo");
					
					if(e == null) {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					} else {
						loggedInUsers.put(id, currentCookies);
						return new Result(true, 0);
					}
					
				} catch (IOException e) {
					if (id == "" || pwd == "") {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					}
					e.printStackTrace();
				}
				return new Result(false, Error.Code.UNKNOWN);
				
			}	
			
			private Response mainActionABEEK(Map<String, String> currentCookies) {
				Connection.Response response = null;
				final String URL ="http://abeek.knu.ac.kr/Keess/comm/support/main/main.action";
				final int TIMEOUT = 5000;
				//final String contentType = "application/x-www-form-urlencoded";
				final String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate, br";
				final String host = "abeek.knu.ac.kr";
				//final String origin = "http://abeek.knu.ac.kr";
				//final String referer = "http://abeek.knu.ac.kr/Keess/comm/support/login/loginForm.action";
				final String connection = "keep-alive";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							//.header("Origin", origin)
							//.header("Referer", referer)
							.header("Connection", connection)
							.userAgent(userAgent)
							.cookies(currentCookies)
							.method(Connection.Method.GET)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Response loginActionABEEK(Map<String, String> currentCookies, Map<String, String> data) {
				Connection.Response response = null;
				final String URL ="https://abeek.knu.ac.kr/Keess/comm/support/login/login.action";
				final int TIMEOUT = 3000;
				final String contentType = "application/x-www-form-urlencoded";
				final String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate, br";
				final String host = "abeek.knu.ac.kr";
				final String origin = "http://abeek.knu.ac.kr";
				final String referer = "http://abeek.knu.ac.kr/Keess/comm/support/login/loginForm.action";
				final String connection = "keep-alive";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.data(data)
							.userAgent(userAgent)
							//.cookies(currentCookies)
							.method(Connection.Method.POST)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}
			
			private Result yesLogin(final String id, final String pwd) {
				
				try {
					Connection.Response currentResponse;
					Map<String, String> currentCookies;
					ObjectMapper m = new ObjectMapper();
					
					currentResponse = loginFormAction();
					currentCookies = currentResponse.cookies();
					
					currentResponse = getAvailableUserDivsAction(currentCookies, id);
					currentCookies = currentResponse.cookies();
					
					currentResponse = getUndergraduateStuNbrsAction(currentCookies, id);
					currentCookies = currentResponse.cookies();
					
					String userDiv = "";
					String nbr = "";
					String json = currentResponse.body();
					if (!json.equals("[]")) {
						m.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
						String[] a = m.readValue(json, String[].class);	
						nbr = a[0];
					}
					
					Map<String, String> data = new HashMap<>();
					
					data.put("user.usr_id", id);
					data.put("user.passwd", pwd);
					data.put("user.user_div", userDiv);
					data.put("user.stu_persnl_nbr", nbr);
					
					currentResponse = loginAction(currentCookies, data);
					currentCookies = currentResponse.cookies();
					
					Document dc = currentResponse.parse();
					//System.out.println(dc.toString());
					
					Elements e = dc.getElementsByClass("box01");
					
					if(e.isEmpty()) {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					} 
					loggedInUsers.put(id, currentCookies);
					return new Result(true, 0);
					
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JsonProcessingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					if (id == "" || pwd == "") {
						return new Result(false, Error.Code.ID_PASSWORD_INCORRECT);
					}
					e.printStackTrace();
				}
				return new Result(false, Error.Code.UNKNOWN);
			}
			
			private Response loginAction(Map<String, String> currentCookies, Map<String, String> data) {
				Connection.Response response = null;
				final String URL ="https://yes.knu.ac.kr/comm/comm/support/login/login.action";
				final int TIMEOUT = 3000;
				final String contentType = "application/x-www-form-urlencoded";
				final String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate, br";
				final String host = "yes.knu.ac.kr";
				final String origin = "https://yes.knu.ac.kr";
				final String referer = "https://yes.knu.ac.kr/comm/comm/support/login/loginForm.action?redirUrl=%2Fcomm%2Fcomm%2Fsupport%2Fmain%2Fmain.action";
				final String connection = "keep-alive";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.data(data)
							.userAgent(userAgent)
							.cookies(currentCookies)
							.method(Connection.Method.POST)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Response getUndergraduateStuNbrsAction(Map<String, String> cookies, String id) {
				Connection.Response response = null;
				final String URL ="https://yes.knu.ac.kr/comm/comm/support/login/getUndergraduateStuNbrs.action";
				final int TIMEOUT = 3000;
				final String contentType = "application/x-www-form-urlencoded; charset=UTF-8";
				final String accept = "text/javascript, text/html, application/xml, text/xml, */*";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate, br";
				final String host = "yes.knu.ac.kr";
				final String origin = "https://yes.knu.ac.kr";
				final String referer = "https://yes.knu.ac.kr/comm/comm/support/login/login.action";
				final String connection = "keep-alive";
				final String xPrototypeVersion = "1.6.1";
				final String xRequestedWith = "XMLHttpRequest";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.header("X-Prototype-Version", xPrototypeVersion)
							.header("X-Requested-With", xRequestedWith)
							.data("user.usr_id", id)
							.ignoreContentType(true)
							.userAgent(userAgent)
							.cookies(cookies)
							.method(Connection.Method.POST)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Response getAvailableUserDivsAction(Map<String, String> loginFormCookie, String id) {
				Connection.Response response = null;
				final String URL ="https://yes.knu.ac.kr/comm/comm/support/login/getAvailableUserDivs.action";
				final int TIMEOUT = 5000;
				final String contentType = "application/x-www-form-urlencoded; charset=UTF-8";
				final String accept = "text/javascript, text/html, application/xml, text/xml, */*";
				final String acceptLanguage = "ko-kr";
				final String acceptEncoding = "gzip, deflate, br";
				final String host = "yes.knu.ac.kr";
				final String origin = "https://yes.knu.ac.kr";
				final String referer = "https://yes.knu.ac.kr/comm/comm/support/login/login.action";
				final String connection = "keep-alive";
				final String xPrototypeVersion = "1.6.1";
				final String xRequestedWith = "XMLHttpRequest";
				
				try {
					response = Jsoup.connect(URL)
							.timeout(TIMEOUT)
							//.header("Content-Type", contentType)
							.header("Accept", accept)
							.header("Accept-Language", acceptLanguage)
							.header("Accept-Encoding", acceptEncoding)
							.header("HOST", host)
							.header("Origin", origin)
							.header("Referer", referer)
							.header("Connection", connection)
							.header("X-Prototype-Version", xPrototypeVersion)
							.header("X-Requested-With", xRequestedWith)
							.data("user.usr_id", id)
							.ignoreContentType(true)
							.userAgent(userAgent)
							.cookies(loginFormCookie)
							.method(Connection.Method.POST)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}

			private Response loginFormAction() {
				
				Connection.Response response = null;
				try {
					response = Jsoup.connect("https://yes.knu.ac.kr/comm/comm/support/login/loginForm.action?redirUrl=%2Fcomm%2Fcomm%2Fsupport%2Fmain%2Fmain.action")
							.timeout(5000)
							.header("HOST", "yes.knu.ac.kr")
							.header("Origin", "https://yes.knu.ac.kr")
							.header("Connection", "keep-alive")
							.header("Referer", "https://yes.knu.ac.kr/comm/")
							.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
							.header("Accept-Encoding", "gzip, deflate, br")
							.header("Accept-Language", "ko-kr")
							.userAgent(userAgent)
							.method(Connection.Method.GET)
							.execute();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return response;
			}
			
			public class Result {
				public boolean isSucceed = false;
				public int errorCode = Error.Code.UNKNOWN;
				public Map<String, Object> output = null;
					
				public Result(final boolean s, final int errorCode) {
					isSucceed = s;
					this.errorCode = errorCode;
				}
				
				public Result(final boolean s, final int errorCode, final Map<String, Object> output) {
					this(s, errorCode);
					this.output = output;
				}
				
				
			}    	
			
			private class Error {
					public class Code {
						public static final int ID_PASSWORD_INCORRECT = 1;
						public static final int CHANGE_PASSWORD = 2;
						public static final int INVALID_REQUEST = 3;
						public static final int USER_NOT_LOGGED_IN = 4;

						public static final int UNKNOWN = 5;
					}
					
					public class Message {
						public static final String ID_PASSWORD_INCORRECT = "";
						public static final String CHANGE_PASSWORD = "";
						public static final String UNKNOWN = "unknown error occurred!";
					}
			}
    	}
    }
    
    
    
    
}
