package com.youku.wireless.guid;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import com.youku.data.driver.MrJobHelper;
import com.youku.data.driver.annotation.Program;
import com.youku.data.driver.annotation.ProgramParam;
import com.youku.data.mapreduce.input.SmartInputFormat;
import com.youku.wireless.utils.DataUtils;

@Program(name = "statis.guid", description = "guid无线设备库,统计访问接口“initial”并且guid唯一的用户设备数。可根据stime和etime参数配合选择起始时间和结束时间。")
public class guid_statis {

	@ProgramParam(name = "stime", description = "起始时间，包含日期。", required = false)
	private String stime = null;
	
	@ProgramParam(name = "etime", description = "结束时间，包含日期。", required = false)
	private String etime = null;

	public static class JobMapper extends
			Mapper<LongWritable, Text, Text, guid_statis_request> {
		
		@Override
		protected void map(LongWritable key, Text value,
				Context context) throws IOException, InterruptedException {
			String valueString = value.toString();
			if(!valueString.contains("initial")){
				return;
			}
			guid_statis_request r = parseRequest(valueString);
			if (r != null) {
				String guid = r.getGuid();
				Text outKey = new Text();
				outKey.set(guid);
				context.write(outKey, r);
			}
		}

		@Override
		protected void setup(Context context) throws IOException,
				InterruptedException {
			//
		}
		
		private final guid_statis_request parseRequest(String line) throws IOException,
				InterruptedException {
			guid_statis_request r = new guid_statis_request(line);
			if (r==null || r.getGuid()==null){
				return null;
			}
			return r;
		}

	}
			
	public static class LogReducer extends Reducer<Text, guid_statis_request, Text, Text> {

		public void reduce(Text key, Iterable<guid_statis_request> values, Context context)
				throws IOException, InterruptedException {
			java.util.Iterator<guid_statis_request> it = values.iterator();
			
			
			long regtime = 0;
			guid_statis_request r_tmp = null;
			guid_statis_request r = null;
			while (it.hasNext()) {
				r_tmp = it.next();
				
				long time = r_tmp.getLongtime();
				if (regtime == 0 || r==null) {
					regtime = time;
					r = r_tmp;
				} else {
					if (regtime > time) {
						regtime = time;
						r = r_tmp;
					}
				}
				
			}
			
			Text outValue = new Text();
			outValue.set(r.getPid() + "\t" + r.getVer() + "\t" 
					+ r.getGuid2()+"\t" + r.getOs() + "\t" + r.getOs_ver() + "\t"
					+ r.getBtype() + "\t" + r.getBrand() + "\t"
					+ r.getOperator() + "\t" + r.getDeviceid() + "\t"
					+ r.getNdeviceid() + "\t" + regtime + "\t"
					+ r.getMac() + "\t" + r.getImei() + "\t" + r.getUuid());
			context.write(key, outValue);
		}
	}
	
	public static class LogFilePathFilter implements PathFilter {
		
		@Override
		public boolean accept(Path path){
			
			System.out.println("path.getParent.Name:"+path.getParent().getName());
			String pathName = path.getName();
			System.out.println("pathName:"+pathName);
			
			if (pathName.contains(".bz2") || pathName.contains("gz")){
				String date1 = path.getParent().getName();
				if(!timeCompare(date1)){
					return false;
				}else{
					return true;
				}
			}else{
				return true;
			}
			
		}
		
		static FileSystem fs;
		static Configuration conf;

		static void setConf(Configuration _conf) {
			conf = _conf;
			
			stime = conf.get("stime");
			etime = conf.get("etime");
		}
		
		private static String stime;
		private static String etime;
		
		public static final boolean timeCompare(String time1){
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
			try {
				Date d = dateFormat.parse(time1);
				Date d1 = dateFormat.parse(stime);
				Date d2 = dateFormat.parse(etime);
				
				
				if (d.getTime() >= d1.getTime() && d.getTime() <= d2.getTime()) {
					return true;
				} else {
					return false;
				}
			} catch (ParseException e) {
				return false;
			}
		}

	}

	public boolean run(Configuration conf, MrJobHelper jobHelper,
			String[] infiles, String outdir) throws IOException {
		Job job = jobHelper.newJob(conf, JobMapper.class, LogReducer.class,
				Text.class, Text.class);
		job.getConfiguration().set("stime", stime);
		job.getConfiguration().set("etime", etime);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(guid_statis_request.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileSystem fstm = FileSystem.get(conf);
		Path outDir = new Path(outdir);
		fstm.delete(outDir, true);
		
		job.setInputFormatClass(SmartInputFormat.class);
		//job.setOutputFormatClass(GuidOutputFormat.class);
		LogFilePathFilter.setConf(job.getConfiguration());
		SmartInputFormat.setInputPathFilter(job, LogFilePathFilter.class);
		SmartInputFormat.setInputFiles(job, infiles);
		FileOutputFormat.setOutputPath(job, new Path(outdir));

		return jobHelper.runJob(job);
//		if(jobHelper.runJob(job)){
//			FsPermission fp = FsPermission.createImmutable((short)660);
//			fstm.setPermission(outDir, fp);
//			return true;
//		}
//		return false;
//		if (jobHelper.runJob(job)){
//			Properties p = new Properties();
//			p.load(this.getClass().getClassLoader().getResourceAsStream("datasource.properties"));
//			DataUtils.importPLPVData(conf, p);
//		}
//		
//		return true;
	}
}
