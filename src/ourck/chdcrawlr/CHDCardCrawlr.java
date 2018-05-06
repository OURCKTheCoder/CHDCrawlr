package ourck.chdcrawlr;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import javax.security.auth.login.LoginException;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.*;
import org.jsoup.Connection.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static ourck.utils.ScreenReader.*;

public class CHDCardCrawlr {
	
	private static final String URL =  // 登录完成后跳转至portal
			"http://ids.chd.edu.cn/authserver/login"
			+ "?service=http%3A%2F%2Fportal.chd.edu.cn%2Findex.portal%3F.pn%3Dp56_p232";

	private Connection ct = Jsoup.connect(URL); // Handle SINGLE connection to avoid logging out.  
	private Map<String, String> cookies;
	
	private class CardPayItem extends LinkedHashMap<String, String>{
		private static final long serialVersionUID = 6027944611629342146L;
		private long studentnum;
		private int cardid;
		private String date;
		private String time;
		private String place;
		private double payval;
		private double restval;
		private String refreshdate;
		public void appendItem(int recordOrder, String val) {
			switch(recordOrder) {
			case 0:
				studentnum = Long.parseLong(val);
				put("学号", "" + studentnum);
				break;
			case 1:
				cardid = Integer.parseInt(val);
				put("卡号", "" + cardid);
				break;
			case 2:
				date = val;
				put("消费日期", "" + date);
				break;
			case 3:
				time = val;
				put("消费时间", "" + time);
				break;
			case 4:
				place = val;
				put("消费地点", "" + place);
				break;
			case 5:
				payval =Double.parseDouble(val);
				put("消费金额", "" + payval);
				break;
			case 6:
				restval =Double.parseDouble(val);
				put("消费后余额", "" + restval);
				break;
			case 7:
				refreshdate = val;
				put("记录更新日期", "" + refreshdate);
				break;
			}
		}
	}
	
	public void login(String userName, String passwd) throws IOException, LoginException {
//	------------------1. Get request to get cookies:------------------
//		 TODO ...Maybe other headers?
		ct.header("User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:29.0) Gecko/20100101 Firefox/29.0");
		Response response = ct.execute(); // "GET"
		
		// Grep page elements for POST:
		Document doc = Jsoup.parse(response.body());
		Element e = doc.getElementById("casLoginForm");
		Map<String, String> params = new HashMap<String, String>();
		{
				params.put("username", userName);
				params.put("password", passwd);
				params.put("btn", "");
				// ... There's a hidden login key called "lt":
				params.put("lt", 
						e.select("input[name=\"lt\"]").get(0).attr("value").toString()); // Only one
				params.put("dllt",
						e.select("input[name=\"dllt\"]").get(0).attr("value").toString());
				params.put("execution", 
						e.select("input[name=\"execution\"]").get(0).attr("value").toString());
				params.put("_eventId", 
						e.select("input[name=\"_eventId\"]").get(0).attr("value").toString());
				params.put("rmShown", 
						e.select("input[name=\"rmShown\"]").get(0).attr("value").toString());
		}
		
//	-----------------2. Send an initialized POST (USING THE SAME CONNECTION!):------------------
		Response loginResponse = ct.ignoreContentType(true)
				.method(Method.POST)
				.data(params)
				.cookies(response.cookies())
				.execute();
		cookies = loginResponse.cookies(); // TODO Save time by saving cookies.
//!		System.out.println(loginResponse.body());
//!		System.out.println(loginResponse.headers());
//!		System.out.println(loginResponse.multiHeaders()); // DEBUG-ONLY
		
		// "Set-Cookie" header means login success.
		if(!loginResponse.hasHeader("Set-Cookie")) 
			throw new LoginException(" [!] Login failed! Maybe try again next time,");
	}
	
	public String cardRecordCrawlr() throws IOException {
		String UrlForTable = null;
		
		Element frameWithAjax = ct.get().getElementById("pf40071");
		Element jsFunctions = frameWithAjax.getElementsByClass("portletContent").get(0) //Only one
				.getElementsByAttributeValue("type", "text/javascript").get(0);
		{
			// A method called "function getItemContent(param)" that matters:
			String[] functions = jsFunctions.data().toString().split("function");  
			String certain = null;
			for(String function : functions) {
				if(function.contains("getItemContent")) {
					certain = function;
					break;
				}
			}
			// This method use Ajax to refresh page. Get its "url" parameter: 
			String[] lines = certain.split("\n");
			String ajaxParam = null;
			for(String line : lines) {
				if(line.contains("url:\"")) {
					ajaxParam = line.trim();
					break;
				}
			}
			ajaxParam = ajaxParam.substring(5,ajaxParam.length() - 2 );
			// A sample url for getting table:
//			UrlForTable = "http://portal.chd.edu.cn/"
//				+ "pnull.portal?rar=0.4902312308445348"
//				+ "&.pmn=view"
//				+ "&.ia=false"
//				+ "&action=showItem"
//				+ "&.pen=pe950"
//				+ "&itemId=601&childId=621"; // (static)
			UrlForTable = "http://portal.chd.edu.cn/" 
					+ ajaxParam
					+ "&itemId=601&childId=621";
		}
		
		Connection ctForTable = Jsoup.connect(UrlForTable);
		Response table = ctForTable.ignoreContentType(true)
				.method(Method.GET)
				.cookies(cookies)
				.execute();
//!		System.out.println(table.body()); // DEBUG-ONLY: Make sure the page hasn't expired.
		
		return table.body();
	}
	
	public void TabletoJsonFile(String res, String filename) throws JSONException, IOException {
		Elements tableCtxt = Jsoup.parse(res)
				.getElementById("sb_table621")
				.getElementsByTag("tbody").get(0)
				.getElementsByTag("tr");
//!		System.out.println(tableCtxt); // DEBUG-ONLY
		System.out.println("-----------------------------------------------------");
		JSONObject jobj = new JSONObject();
		List<CardPayItem> itemsList = new ArrayList<>();
		
		// 1. Addng items to a list - for serilizing.
		for(Element item : tableCtxt) {
			System.out.println(item);
			System.out.println("-----------------------------------------------------");
			List<Element> records = item.getElementsByTag("td");
			CardPayItem singleitem = new CardPayItem();
			for(int i = 0; i < records.size(); i++) {
				singleitem.appendItem(i, records.get(i).ownText());
			}
			itemsList.add(singleitem);
		}
		
		// 2. JSON.write() & Serialize.
		
		for(int i = 0; i < itemsList.size(); i++) {
//!			System.out.println(c);
			jobj.put("" + i, itemsList.get(i));
		}
		StringWriter writer = new StringWriter();
		BufferedWriter bwt = new BufferedWriter(
				new FileWriter(filename));
		jobj.write(bwt);
		bwt.write(writer.toString());
		bwt.close();
	}
	
	public static void main(String[] args) throws IOException {
		String num, pwd;
		System.out.println(" - Plz input your account:");
		num = jin();
		System.out.println(" - Input your Passwd (won't show on console):");
		pwd = jinPwd();
		System.out.println(" - Loading...");
		
		CHDCardCrawlr cwlr = new CHDCardCrawlr();
		try {
			cwlr.login(num, pwd);
			String table = cwlr.cardRecordCrawlr();
			cwlr.TabletoJsonFile(table, "test.json");
		} catch(LoginException e) {
			System.err.println(e.getMessage());
		}
	}
	
}
