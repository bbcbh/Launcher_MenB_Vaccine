package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

public class Runnable_MenB_Vaccine_MSM extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_MSM");

	private static final int num_inf = 3; // TP, NG and CT
	private static final int num_site = 4;
	private static final int num_act = 5;

	// FILENAME_PREVALENCE_PERSON, FILENAME_CUMUL_INCIDENCE_PERSON
	private static final int[] COL_SEL_INF_GENDER = new int[] { 6 };
	// FILENAME_CUMUL_INCIDENCE_SITE
	private static final int[] COL_SEL_INF_GENDER_SITE = new int[] { 25, 26, 27 };
	// "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_SITE
	private static final int[] COL_SEL_INF_SITE = new int[] { 5, 6, 7 };

	//protected double[] current_vaccine_allocation = null;

	public Runnable_MenB_Vaccine_MSM(long cMap_seed, long sim_seed, Properties prop) {
		super(cMap_seed, sim_seed, prop, num_inf, num_site, num_act);
	}

	@Override
	public int getPersonGrp(Integer personId) {
		// MSM only
		return 2;
	}	

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();

		String key, fileName;
		HashMap<Integer, int[]> countMap;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();

		if (sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE) != null) {
			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE);
			fileName = String.format(filePrefix + "Vaccine_Coverage_%d_%d.csv", cMAP_SEED, sIM_SEED);

			try {
				PrintWriter pWri = new PrintWriter(new java.io.File(baseDir, fileName));
				Integer[] timeArr = countMap.keySet().toArray(new Integer[0]);
				Arrays.sort(timeArr);

				pWri.println("Time,Ever_Vaccinated,Multi_Doses,Last_Dose_5YPlus");
				for (Integer time : timeArr) {
					int[] ent = countMap.get(time);
					pWri.printf("%d,%d,%d,%d\n", time, ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_EVER_VACCINATED],
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_MULTI_DOSES],
							ent[SIM_OUTPUT_INDEX_VACC_COVERAGE_LAST_DOSE_5YRPLUS]);
				}

				pWri.close();
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}

		}
//		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE) != 0) {
//			key = String.format(SIM_OUTPUT_KEY_CUMUL_TREATMENT,
//					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE);
//
//			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
//			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_TREATMENT_PERSON,
//					cMAP_SEED, sIM_SEED);
//			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP },
//					COL_SEL_INF_GENDER);
//
//		}
		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE_SITE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);

			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_SITE,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d_Site_%d", new int[] { NUM_INF, NUM_GRP, NUM_SITE },
					COL_SEL_INF_GENDER_SITE);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_GENDER_COUNT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(
					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_SITE_COUNT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(
					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_SITE,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Site_%d", new int[] { NUM_INF, NUM_SITE }, COL_SEL_INF_SITE);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_TRACK_INFECTION_HISTORY) > 0) {

			PrintWriter pWri;
			Integer[] pids = infection_history.keySet().toArray(new Integer[infection_history.size()]);
			Arrays.sort(pids);
			try {
				pWri = new PrintWriter(new File(baseDir,
						String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_INFECTION_HISTORY,
								cMAP_SEED, sIM_SEED)));
				pWri.println("ID,INF_ID,History");
				for (Integer pid : pids) {
					ArrayList<ArrayList<Integer>> hist = infection_history.get(pid);
					for (int infId = 0; infId < hist.size(); infId++) {
						pWri.print(pid.toString());
						pWri.print(',');
						pWri.print(infId);
						for (Integer timeEnt : hist.get(infId)) {
							pWri.print(',');
							pWri.print(timeEnt);
						}
						pWri.println();
					}
				}

				pWri.close();
			} catch (IOException e) {
				e.printStackTrace(System.err);
			}
		}

		if (print_progress != null && runnableId != null) {
			try {
				print_progress.printf("Post simulation file generation for Thread <%s> completed. Timestamp = %tc.\n",
						runnableId, System.currentTimeMillis());
			} catch (Exception ex) {
				System.err.printf("Post simulation file generation for Thread <%s> completed.\n", runnableId);
			}
		}

	}
	
	protected int getVaccineGrp(int pid) {
		return risk_cat_map.get(pid);
	}

}
