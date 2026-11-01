package com.meteordevelopments.duels.replay.util.fetcher;

import java.io.BufferedReader;



import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;





import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.meteordevelopments.duels.replay.util.LogUtils;


public class WebsiteFetcher {
	

	
	private static Gson gson = new GsonBuilder().create();
	

	// Bounded and thread-safe: entries are keyed by player name and written from the async pool
	private static Map<String, String> webChache = CacheBuilder.newBuilder()
			.maximumSize(1000)
			.expireAfterAccess(1, TimeUnit.HOURS)
			.<String, String>build()
			.asMap();

	private static ExecutorService pool = Executors.newCachedThreadPool();
	
	private String content;
		
	public WebsiteFetcher(String content){
		this.content = content;
	}



	public static void getContent(final String url,final boolean cache, Consumer<String> action) {
		pool.execute(new Acceptor<String>(action) {

			@Override
			public String getValue() {
				return getContent(url,cache);
			}
			
		});
	}
	public static void getJson(final String url,final boolean cache,final JsonData jsonData, Consumer<JsonClass> action) {
		pool.execute(new Acceptor<JsonClass>(action) {

			@Override
			public JsonClass getValue() {
				return getJson(url,cache,jsonData);
			}
			
		});
	}
	public static JsonClass getJson(String url, boolean cache, JsonData jsonData) {
		WebsiteFetcher data;
		try {
		final String cached = cache ? webChache.get(url) : null;
		if(cached != null) {
			 data = gson.fromJson(cached, WebsiteFetcher.class);

		}else{
		
				HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
				connection.setReadTimeout(5000);
				
	   StringBuilder sb = new StringBuilder();
				   
				   for(Scanner sc = new Scanner(new BufferedReader(new InputStreamReader(connection.getInputStream()))); sc.hasNext(); )
				      sb.append(sc.nextLine()).append('\n');
				   data = new WebsiteFetcher(sb.toString());
				
		}
		webChache.put(url, gson.toJson(data));
		
					jsonData.setData(data.content);
					jsonData.convertData();
			return jsonData.getJsonClass();
			
			
		} catch (Exception e) {
			LogUtils.log("Could not retrieve player profile: " + e.getMessage());
		}
		
		return null;
	}

	public static String getContent(String url, boolean cache) {
		WebsiteFetcher data;
		try {
		final String cached = cache ? webChache.get(url) : null;
		if(cached != null) {
			 data = gson.fromJson(cached, WebsiteFetcher.class);

		}else{
		
				HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
				connection.setReadTimeout(5000);
					
	   StringBuilder sb = new StringBuilder();
				   
				   for(Scanner sc = new Scanner(new BufferedReader(new InputStreamReader(connection.getInputStream()))); sc.hasNext(); )
				      sb.append(sc.nextLine()).append('\n');
				   data = new WebsiteFetcher(sb.toString());
				
		}
		webChache.put(url, gson.toJson(data));

			
			return data.content;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	

	

}
