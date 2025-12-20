package test;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test_Analyse_MenB_Vaccine {

	public static void main(String[] args) throws IOException {
		File baseDir = new File("C:\\Users\\bhui\\Documents\\Java_Test\\Test_SimClusterModel_Transmission_MenB_Vaccine_MSM");
		
		Pattern pattern_num_inf_src = Pattern.compile("Infectious_Prevalence_Person_(-?\\d+).csv.7z");
		Pattern pattern_inf_preval_header = Pattern
				.compile("\\[(.+),(\\d+)\\]Infectious_Prevalence_Person_(-?\\d+)_(-?\\d+).csv");


		File[] resDirs = baseDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().startsWith("ResultSet_");
			}
		});

		long tic = System.currentTimeMillis();
		
		for (File resDir : resDirs) {
			
			File[] zips = resDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pattern_num_inf_src.matcher(pathname.getName()).matches();
				}
			});
			
			Comparator<String> zipKeyComp = new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					Matcher m1 = pattern_inf_preval_header.matcher(o1);
					Matcher m2 = pattern_inf_preval_header.matcher(o2);
					if (m1.matches() && m2.matches()) {
						int res = Integer.compare(Integer.parseInt(m1.group(2)), Integer.parseInt(m2.group(2)));
						if (res == 0) {
							res = Long.compare(Long.parseLong(m1.group(2)), Long.parseLong(m2.group(2)));
						}
						return res;

					} else {
						return o1.compareTo(o2);
					}

				}
			};
			ArrayList<StringBuilder> lines_inf = new ArrayList<>();
			
			for(File zip : zips) {
				HashMap<String, ArrayList<String[]>> linesMap = util.Util_7Z_CSV_Entry_Extract_Callable
						.extractedLinesFrom7Zip(zip);
				String[] keys = linesMap.keySet().toArray(new String[0]);
				Arrays.sort(keys,zipKeyComp);
				
				
				for (String key : keys) {
					ArrayList<String[]> lines_from_sim = linesMap.get(key);
					for (int lineNum = 0; lineNum < lines_from_sim.size(); lineNum++) {
						String[] line_ent = lines_from_sim.get(lineNum);
						while (lineNum >= lines_inf.size()) {
							lines_inf.add(new StringBuilder());
						}
						StringBuilder strBuild = lines_inf.get(lineNum);
						if (strBuild.length() == 0) {
							strBuild.append(line_ent[0]);
						}
						strBuild.append(',');
						if (lineNum == 0) {
							Matcher m = pattern_inf_preval_header.matcher(key);
							if (m.find()) {
								strBuild.append(m.group(2));
								strBuild.append(":(");
								strBuild.append(m.group(3));
								strBuild.append('_');
								strBuild.append(m.group(4));
								strBuild.append(")");
							} else {
								strBuild.append(key);
							}
						} else {												
							strBuild.append(line_ent[1]);
						}

					}
				}
				
				
				
			}
			
			PrintWriter pWri = new PrintWriter(new File(resDir, "Num_of_infected.csv"));
			for (StringBuilder lines : lines_inf) {
				pWri.println(lines.toString());
			}
			pWri.close();

			
			
			System.out.printf("Analyse results CSV for %s extracted.\n Time elpased = %.3fs\n",
					resDir.getAbsolutePath(), (System.currentTimeMillis() - tic) / 1000f);

		}
		
		System.out.printf("Analyse results CSV completed.\n Time elpased = %.3fs\n", (System.currentTimeMillis() - tic) / 1000f);

	}

}
