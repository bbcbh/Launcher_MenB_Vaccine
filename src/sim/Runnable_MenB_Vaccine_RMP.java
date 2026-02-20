package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Pattern;

public class Runnable_MenB_Vaccine_RMP extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_RMP");

	private static final int NUM_INF = 1; // NG
	private static final int NUM_SITE = 2; // 0 = Penile, 1=Vaginal
	private static final int NUM_ACT = 1; // Penile-vaginal sex only

	protected static final String SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP = "SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP";
	protected static final String SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP = "SIM_OUTPUT_KEY_TREATMENT_BY_LOC_GRP";

	protected int[][] cumul_treatment_by_loc_grp;

	public Runnable_MenB_Vaccine_RMP(long cMap_seed, long sim_seed, Properties prop) {
		super(cMap_seed, sim_seed, prop, NUM_INF, NUM_SITE, NUM_ACT);
	}

	protected int getVaccineGrp(int pid) {
		return getPersonGrp(pid);
	}

	@Override
	protected void applyTreatment(int currentTime, int infId, int pid, int[][] inf_stage) {
		super.applyTreatment(currentTime, infId, pid, inf_stage);

		if (cumul_treatment_by_loc_grp == null) {
			cumul_treatment_by_loc_grp = new int[NUM_GRP][location_name.length];
		}
		int[] indiv_stat = indiv_map.get(pid);
		int grp = indiv_stat[INDIV_MAP_CURRENT_GRP];
		if (grp >= 0) {
			int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
			int locPt = map_location.get(Integer.toString(location));
			cumul_treatment_by_loc_grp[grp][locPt]++;
		}

	}

	@SuppressWarnings("unchecked")
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
				out.printf("%sT = %d, Cumul. incidence #%d = %d. Generated at %tc\n", filePrefix, currentTime, inf,
						cumul_incid, System.currentTimeMillis());
			}

			// Add grp-location stat
			HashMap<Integer, int[][]> countGrpLoc;
			// Prevalence
			countGrpLoc = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP));
			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP, countGrpLoc);
			}

			int[][] countEnt = new int[NUM_GRP][location_name.length];
			ArrayList<Integer> infected_id_arr = new ArrayList<>();

			for (Entry<String, ArrayList<Integer>> ent : map_currently_infectious.entrySet()) {
				for (Integer pid : ent.getValue()) {
					int index = Collections.binarySearch(infected_id_arr, pid);
					if (index < 0) {
						int[] indiv_stat = indiv_map.get(pid);
						int grp = indiv_stat[INDIV_MAP_CURRENT_GRP];
						if (grp >= 0) {
							int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
							int locPt = map_location.get(Integer.toString(location));
							countEnt[grp][locPt]++;
						}
						infected_id_arr.add(~index, pid);
					}
				}
			}
			countGrpLoc.put(currentTime, countEnt);

			// Cumulative treatment
			countGrpLoc = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP));

			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP, countGrpLoc);
			}

			int[][] export_cumul_treatment = new int[NUM_GRP][location_name.length];
			if (cumul_treatment_by_loc_grp != null) {
				for (int g = 0; g < export_cumul_treatment.length; g++) {
					export_cumul_treatment[g] = Arrays.copyOf(cumul_treatment_by_loc_grp[g], location_name.length);
				}
			}
			countGrpLoc.put(currentTime, export_cumul_treatment);

		}

	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		super.postSimulation();

		String key, fileName;
		HashMap<Integer, int[]> countMap;
		String filePrefix = this.getRunnableId() == null ? "" : this.getRunnableId();
		final int[] COL_SEL_INF_GENDER = null;

		File seedFileDir = this.getSim_prop().containsKey(PROP_SEED_FILE_PATH)
				? new File((String) this.getSim_prop().get(PROP_SEED_FILE_PATH)).getParentFile()
				: baseDir;

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE) != 0) {

			key = String.format(SIM_OUTPUT_KEY_CUMUL_INCIDENCE,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_INCIDENCE_FILE);
			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_INCIDENCE_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Group_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE) != 0) {
			key = String.format(SIM_OUTPUT_KEY_CUMUL_TREATMENT,
					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_TREATMENT_FILE);

			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
			fileName = String.format(filePrefix + Simulation_ClusterModelTransmission.FILENAME_CUMUL_TREATMENT_PERSON,
					cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			HashMap<Integer, int[][]> countGrpLoc = (HashMap<Integer, int[][]>) sim_output
					.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Treatment_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);
			File file_grp_loc = new File(seedFileDir, fileName);
			printLocGrpCount(countGrpLoc, file_grp_loc);

		}

		if ((simSetting & 1 << Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE) != 0) {

//			key = String.format(SIM_OUTPUT_KEY_INFECTIOUS_GENDER_COUNT,
//					Simulation_ClusterModelTransmission.SIM_SETTING_KEY_GEN_PREVAL_FILE);
//			countMap = (HashMap<Integer, int[]>) sim_output.get(key);
//			fileName = String.format(
//					filePrefix + "Infectious_" + Simulation_ClusterModelTransmission.FILENAME_PREVALENCE_PERSON,
//					cMAP_SEED, sIM_SEED);
//			printCountMap(countMap, fileName, "Inf_%d_Gender_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			HashMap<Integer, int[][]> countGrpLoc = (HashMap<Integer, int[][]>) sim_output
					.get(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Infectious_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);

			File file_grp_loc = new File(seedFileDir, fileName);
			printLocGrpCount(countGrpLoc, file_grp_loc);

		}

	}

	private void printLocGrpCount(HashMap<Integer, int[][]> countGrpLoc, File file_grp_loc) {
		try {
			PrintWriter pri_grp_loc = new PrintWriter(file_grp_loc);
			StringBuilder header = new StringBuilder();
			header.append("Time");
			for (int g = 0; g < NUM_GRP; g++) {
				for (int loc = 0; loc < location_name.length; loc++) {
					header.append(',');
					header.append(String.format("Grp_%d_Loc_%s", g, location_name[loc]));
				}
			}
			pri_grp_loc.println(header.toString());

			Integer[] time_arr = countGrpLoc.keySet().toArray(new Integer[0]);
			Arrays.sort(time_arr);

			for (int t : time_arr) {
				pri_grp_loc.print(t);
				int[][] ent = countGrpLoc.get(t);
				for (int g = 0; g < NUM_GRP; g++) {
					for (int loc = 0; loc < location_name.length; loc++) {
						pri_grp_loc.print(',');
						pri_grp_loc.print(ent[g][loc]);
					}
				}
				pri_grp_loc.println();
			}

			pri_grp_loc.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

}
