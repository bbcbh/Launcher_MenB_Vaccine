package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

public class Runnable_MenB_Vaccine_RMP extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_RMP");

	private static final int NUM_INF = 1; // NG
	private static final int NUM_SITE = 2; // 0 = Penile, 1=Vaginal
	private static final int NUM_ACT = 1; // Penile-vaginal sex only


	public Runnable_MenB_Vaccine_RMP(long cMap_seed, long sim_seed, Properties prop) {
		super(cMap_seed, sim_seed, prop, NUM_INF, NUM_SITE, NUM_ACT);	
	}

	protected int getVaccineGrp(int pid) {
		return getPersonGrp(pid);
	}

	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);	
		// Others steps

		if (currentTime % nUM_TIME_STEPS_PER_SNAP == 0) {
			String filePrefix = this.getRunnableId() == null ? "" : String.format("%s ", this.getRunnableId());
			PrintStream out = print_progress == null ? System.out : print_progress;

			for (int inf = 0; inf < cumul_incidence_by_person.length; inf++) {
				int cumul_incid = 0;
				for (int g = 0; g < cumul_incidence_by_person[inf].length; g++) {
					cumul_incid += cumul_incidence_by_person[inf][g];
				}
				out.printf("%sT = %d, Cumul. incidence #%d = %d. Generated at %tc\n", filePrefix, currentTime, inf, cumul_incid, System.currentTimeMillis());
			}

		}

	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();

		String key, fileName;
		HashMap<Integer, int[]> countMap;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();
		PrintWriter pWri;
		final int[] COL_SEL_INF_GENDER = null;

		if (sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE) != null) {
			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTPUT_KEY_VACC_COVERAGE);
			fileName = String.format(filePrefix + "Vaccine_Coverage_%d_%d.csv", cMAP_SEED, sIM_SEED);

			try {
				pWri = new PrintWriter(new java.io.File(baseDir, fileName));
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

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Group_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_GENDER_COUNT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(
					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

		}
		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_TRACK_INFECTION_HISTORY) > 0) {

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
	}

}
