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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import random.RandomGenerator;

public class Runnable_MenB_Vaccine_RMP extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_RMP");

	private static final int NUM_INF = 1; // NG
	private static final int NUM_SITE = 2; // 0 = Penile, 1=Vaginal
	private static final int NUM_ACT = 1; // Penile-vaginal sex only

	protected static final String SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP = "SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP";
	protected static final String SIM_OUTPUT_KEY_PREVALENCE_BY_REG_GRP = "SIM_OUTPUT_KEY_PREVALENCE_BY_REG_GRP";
	protected static final String SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP = "SIM_OUTPUT_KEY_TREATMENT_BY_LOC_GRP";
	protected static final String SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED = "SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED";
	protected static final String SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED_BY_LOC_GRP = "SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED_BY_LOC_GRP";

	// Adjust based on regions
	protected static final int FIELD_ACT_FREQ_TRAN_ADJ_CITY = FIELD_ACT_FREQ_USAGE_CASUAL + 1;
	protected static final int FIELD_ACT_FREQ_TRAN_ADJ_REGIONAL = FIELD_ACT_FREQ_TRAN_ADJ_CITY + 1;
	protected static final int FIELD_ACT_FREQ_TRAN_ADJ_REMOTE = FIELD_ACT_FREQ_TRAN_ADJ_REGIONAL + 1;

	protected int[][] cumul_treatment_by_loc_grp;
	protected int[][] cumul_treatment_missed_by_loc_grp;
	protected int[][] cumul_treatment_missed_by_person;

	// K = Grp, V = double[] {treatment_miss_city, treatment_miss_regional,
	// treatment_miss_remote}
	protected HashMap<Integer, double[]> treatment_miss_by_grp = new HashMap<>();

	protected String[] region_name = new String[] { "CITY", "REGIONAL", "REMOTE" };

	// K = pid, V = region_index (0 = CITY, 1 = REGIONAL, 2 = REMOTE)
	private HashMap<Integer, Integer> map_pid_region = new HashMap<>();

	public static final String FILE_REGION_SPREAD = "Pop_IREG_RA.csv";

	// K = Region_Id, V = {grp, region_spread}
	protected HashMap<Integer, HashMap<Integer, double[]>> map_regions_spread_by_locGrp = null;

	public static final int FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ = FIELD_SOUGHT_TEST_RATE + 1;
	public static final int FIELD_SOUGHT_TEST_RATE_RMP_REGIONAL_ADJ = FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ + 1;
	public static final int FIELD_SOUGHT_TEST_RATE_RMP_REMOTE_ADJ = FIELD_SOUGHT_TEST_RATE_RMP_REGIONAL_ADJ + 1;

	// RNG
	protected RandomGenerator rng_region;

	public Runnable_MenB_Vaccine_RMP(long cMap_seed, long sim_seed, Properties prop) {
		super(cMap_seed, sim_seed, prop, NUM_INF, NUM_SITE, NUM_ACT);
		rng_region = rng_vaccine;

		File file_region_info = new File(prop.getProperty("PROP_CONTACT_MAP_LOC"));
		file_region_info = new File(file_region_info, FILE_REGION_SPREAD);

		if (file_region_info.isFile()) {
			map_regions_spread_by_locGrp = new HashMap<>();
			try {
				String[] ent = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_region_info);
				for (int lineNum = 1; lineNum < ent.length; lineNum++) {
					String[] line_ent = ent[lineNum].split(",");

					Integer ireg = Integer.valueOf(line_ent[0]);
					Integer grp = Integer.valueOf(line_ent[1]);
					double[] prob_region = new double[line_ent.length - 2];

					double pre_sum = 0;
					for (int i = 0; i < prob_region.length; i++) {
						prob_region[i] = Double.parseDouble(line_ent[i + 2]) + pre_sum;
						pre_sum = prob_region[i];
					}
					for (int i = 0; i < prob_region.length; i++) {
						prob_region[i] = prob_region[i] / prob_region[prob_region.length - 1];
					}

					HashMap<Integer, double[]> region_map = map_regions_spread_by_locGrp.get(ireg);
					if (region_map == null) {
						region_map = new HashMap<>();
						map_regions_spread_by_locGrp.put(ireg, region_map);
					}

					region_map.put(grp, prob_region);
				}

				if (print_progress != null) {
					System.out.printf("Regions information loaded from %s.\n", file_region_info.getAbsolutePath());
				}
			} catch (IOException ex) {
				ex.printStackTrace(System.err);

			}			
			
		}

	}

	protected int getVaccineGrp(int pid) {
		return getPersonGrp(pid);
	}

	@Override
	protected double getSeekTestRate(Integer pid) {
		double seek_test_rate = super.getSeekTestRate(pid);
		if (map_regions_spread_by_locGrp != null) {
			int[] indiv_ent = indiv_map.get(pid);
			int g = indiv_ent[INDIV_MAP_CURRENT_GRP];
			double[][] sym_seek_rate_field = (double[][]) getRunnable_fields()[RUNNABLE_FIELD_TRANSMISSION_SOUGHT_TEST_PERIOD_BY_SYM];
			for (double[] ent : sym_seek_rate_field) {
				if (ent.length >= FIELD_SOUGHT_TEST_RATE_RMP_REMOTE_ADJ) {
					int gender_inc = (int) ent[FIELD_SOUGHT_TEST_PERIOD_BY_SYM_GENDER_INC];
					if ((gender_inc & 1 << g) != 0) {
						int regPt = getPersonRegion(pid, indiv_ent).intValue();
//						int home_loc = indiv_ent[INDIV_MAP_HOME_LOC];
//						double[] region_spread = map_regions_spread_by_locGrp.get(home_loc).get(g);
//						double pRegion = rng_region.nextDouble();
//						regPt = Arrays.binarySearch(region_spread, pRegion);
//						if (regPt < 0) {
//							regPt = ~regPt;
//						}
						seek_test_rate *= ent[FIELD_SOUGHT_TEST_RATE_RMP_CITY_ADJ + regPt];
						break;
					}
				}
			}
		}

		return seek_test_rate;
	}

	@Override
	public ArrayList<Integer> loadOptParameter(String[] parameter_settings, double[] point, int[][] seedInfectNum,
			boolean display_only) {

		Pattern test_rate_pattern = Pattern.compile("21_(\\d+)_(\\d+)"); // POP_PROP_INIT_PREFIX_21
		for (int i = 0; i < parameter_settings.length; i++) {
			Matcher m = test_rate_pattern.matcher(parameter_settings[i]);
			if (m.matches()) {
				int row_inc = Integer.parseInt(m.group(1));
				int ent_inc = Integer.parseInt(m.group(2));
				double[][] testRateDefs = (double[][]) getRunnable_fields()[RUNNABLE_FIELD_TRANSMISSION_TESTING_RATE_BY_RISK_CATEGORIES];

				for (int tR = 0; tR < testRateDefs.length; tR++) {
					if ((row_inc & (1 << tR)) != 0) {
						if (ent_inc >= 1 << testRateDefs[tR].length) {
							int gIncl = (int) testRateDefs[tR][FIELD_TESTING_RATE_BY_RISK_CATEGORIES_GENDER_INCLUDE_INDEX];
							int repPt_incl = (int) testRateDefs[tR][FIELD_TESTING_RATE_BY_RISK_CATEGORIES_RISK_GRP_INCLUDE_INDEX];
							double[] ent = treatment_miss_by_grp.get(gIncl);
							for (int r = 0; r < ent.length; r++) {
								if ((repPt_incl & (1 << r)) != 0) {
									ent[r] = point[i];
								}
							}
						}
					}

				}

			}
		}

		return super.loadOptParameter(parameter_settings, point, seedInfectNum, display_only);
	}

	@Override
	public void scheduleNextTest(Integer personId, int lastTestTime, int mustTestBefore, int last_test_infIncl,
			int last_test_siteIncl) {

		// Overwrite to include region rather than risk
		int[] indiv_stat = indiv_map.get(personId);

		Integer regionCat = getPersonRegion(personId, indiv_stat);

		// Testing rate definitions
		double[][] testRateDefs = (double[][]) getRunnable_fields()[RUNNABLE_FIELD_TRANSMISSION_TESTING_RATE_BY_RISK_CATEGORIES];

		for (int i = 0; i < testRateDefs.length; i++) {
			double[] testRateDef = testRateDefs[i];
			int gIncl = (int) testRateDef[FIELD_TESTING_RATE_BY_RISK_CATEGORIES_GENDER_INCLUDE_INDEX];
			int iIncl = (int) testRateDef[FIELD_TESTING_RATE_BY_RISK_CATEGORIES_INF_INCLUDE_INDEX];
			int rIncl = (int) testRateDef[FIELD_TESTING_RATE_BY_RISK_CATEGORIES_RISK_GRP_INCLUDE_INDEX];
			int sIncl = (int) testRateDef[FIELD_TESTING_RATE_BY_RISK_CATEGORIES_SITE_INCLUDE_INDEX];

			if (((gIncl & (1 << indiv_stat[INDIV_MAP_CURRENT_GRP])) != 0) && ((rIncl & (1 << regionCat)) != 0)
					&& (last_test_infIncl < 0 || last_test_infIncl == iIncl)
					&& (last_test_siteIncl < 0 || last_test_siteIncl == sIncl)
					&& testRateDef[FIELD_TESTING_RATE_BY_RISK_CATEGORIES_TEST_RATE_PARAM_START] != -1) {
				scheduleNextTestByDefNum(personId, i, lastTestTime, mustTestBefore);
			}

		}

		// super.scheduleNextTest(personId, lastTestTime, mustTestBefore,
		// last_test_infIncl, last_test_siteIncl);
	}

	protected Integer getPersonRegion(Integer personId, int[] indiv_stat) {
		Integer regionCat = map_pid_region.get(personId);

		if (regionCat == null) {
			// 0 = City, 1 = Regional, 2 = Remote;
			double[] region_spread = map_regions_spread_by_locGrp.get(indiv_stat[INDIV_MAP_HOME_LOC])
					.get(indiv_stat[INDIV_MAP_CURRENT_GRP]);
			double pRegion = rng_region.nextDouble();
			regionCat = Arrays.binarySearch(region_spread, pRegion);
			if (regionCat < 0) {
				regionCat = ~regionCat;
			}
			map_pid_region.put(personId, regionCat);
		}
		return regionCat;
	}

	@Override
	public void refreshField(int fieldId, int currentTime, boolean clearAll, String orginal_field) {
		if (fieldId == RUNNABLE_FIELD_TRANSMISSION_TESTING_RATE_BY_RISK_CATEGORIES) {
			// Search and extract treatment miss setting to treatment_miss_by_grp
			double[][] testRateDefs = (double[][]) getRunnable_fields()[RUNNABLE_FIELD_TRANSMISSION_TESTING_RATE_BY_RISK_CATEGORIES];
			for (int tR = 0; tR < testRateDefs.length; tR++) {
				if (testRateDefs[tR][FIELD_TESTING_RATE_BY_RISK_CATEGORIES_TEST_RATE_PARAM_START] != -1) {
					// Search for treatment miss setting
					for (int i = FIELD_TESTING_RATE_BY_RISK_CATEGORIES_TEST_RATE_PARAM_START
							+ 1; i < testRateDefs[tR].length; i++) {
						if (testRateDefs[tR][i] == -1) {
							int gIncl = (int) testRateDefs[tR][FIELD_TESTING_RATE_BY_RISK_CATEGORIES_GENDER_INCLUDE_INDEX];
							int rIncl = (int) testRateDefs[tR][FIELD_TESTING_RATE_BY_RISK_CATEGORIES_RISK_GRP_INCLUDE_INDEX]; // RISK
																																// =
																																// region
							double[] ent = treatment_miss_by_grp.get(gIncl);
							if (ent == null) {
								ent = new double[region_name.length]; // 0 = CITY, 1 = REGIONAL, 2 = REMOTE
								treatment_miss_by_grp.put(gIncl, ent);
							}
							for (int r = 0; r < ent.length; r++) {
								if ((rIncl & (1 << r)) != 0) {
									ent[r] = testRateDefs[tR][i + 1];
								}
							}
							testRateDefs[tR] = Arrays.copyOf(testRateDefs[tR], i);

							break;
						}
					}
				}
			}
		}
		super.refreshField(fieldId, currentTime, clearAll, orginal_field);
	}

	@Override
	protected void applyTreatment(int currentTime, int infId, int pid_t, int[][] inf_stage) {

		if (cumul_treatment_by_loc_grp == null) {
			cumul_treatment_by_loc_grp = new int[NUM_GRP][location_name.length];
		}
		if (cumul_treatment_missed_by_person == null) {
			cumul_treatment_missed_by_person = new int[NUM_INF][NUM_GRP];
		}
		if (cumul_treatment_missed_by_loc_grp == null) {
			cumul_treatment_missed_by_loc_grp = new int[NUM_GRP][location_name.length];
		}

		// Test for treatment miss by region

		int pid = Math.abs(pid_t);
		int[] indiv_stat = indiv_map.get(pid);
		int grp = indiv_stat[INDIV_MAP_CURRENT_GRP];
		boolean treatment_missed = pid_t >= 0; // based on symptom if pid_t < 0

		if (treatment_missed) {
			int regPt = getPersonRegion(pid, indiv_stat);
			treatment_missed = false;

			for (Entry<Integer, double[]> grp_inc_ent : treatment_miss_by_grp.entrySet()) {
				if ((grp_inc_ent.getKey().intValue() & (1 << grp)) != 0) {
					double[] treatment_miss_by_grp_region = grp_inc_ent.getValue();
//					int homeLoc = indiv_stat[INDIV_MAP_HOME_LOC];
//					double[] region_spread = map_regions_spread_by_locGrp.get(homeLoc).get(grp);													
//					double pRegion = rng_region.nextDouble();
//					int regPt = Arrays.binarySearch(region_spread, pRegion);
//					if (regPt < 0) {
//						regPt = ~regPt;
//					}
					treatment_missed = rng_region.nextDouble() < treatment_miss_by_grp_region[regPt];
					break;
				}
			}
		}

		if (!treatment_missed) {
			super.applyTreatment(currentTime, infId, pid, inf_stage);
			if (grp >= 0) {
				int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
				int locPt = map_location.get(Integer.toString(location));
				cumul_treatment_by_loc_grp[grp][locPt]++;
			}
		} else {
			if (grp >= 0) {
				int location = indiv_map.get(pid)[INDIV_MAP_CURRENT_LOC];
				int locPt = map_location.get(Integer.toString(location));
				cumul_treatment_missed_by_person[0][grp]++;
				cumul_treatment_missed_by_loc_grp[grp][locPt]++;
			}

		}

	}

	@SuppressWarnings("unchecked")
	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);		
		// Others steps
		if (currentTime % nUM_TIME_STEPS_PER_SNAP == 0) {
			String filePrefix = this.getRunnableId() == null ? "" : String.format("%s ", this.getRunnableId());

			if (this.print_progress != null) {
				PrintStream out = print_progress == null ? System.out : print_progress;

				for (int inf = 0; inf < cumul_incidence_by_person.length; inf++) {
					int cumul_incid = 0;
					for (int g = 0; g < cumul_incidence_by_person[inf].length; g++) {
						cumul_incid += cumul_incidence_by_person[inf][g];
					}
					out.printf("%sT = %d, Cumul. incidence #%d = %d. Generated at %tc\n", filePrefix, currentTime, inf,
							cumul_incid, System.currentTimeMillis());
				}
			}

			// Add grp-location stat
			HashMap<Integer, int[][]> countGrpLoc, countGrpRegion;
			// Prevalence by location
			countGrpLoc = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP));
			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_PREVALENCE_BY_LOC_GRP, countGrpLoc);
			}

			// Prevalence by region
			countGrpRegion = ((HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_PREVALENCE_BY_REG_GRP));
			if (countGrpRegion == null) {
				countGrpRegion = new HashMap<>();
				sim_output.put(SIM_OUTPUT_KEY_PREVALENCE_BY_REG_GRP, countGrpRegion);
			}

			int[][] countEntGrpLoc = new int[NUM_GRP][location_name.length];
			int[][] countEntGrpRegion = new int[NUM_GRP][region_name.length]; // CITY, REGIONAL, REMOTE
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
							int regionPt = getPersonRegion(pid, indiv_stat);
							countEntGrpLoc[grp][locPt]++;
							countEntGrpRegion[grp][regionPt]++;
						}
						infected_id_arr.add(~index, pid);
					}
				}
			}
			countGrpLoc.put(currentTime, countEntGrpLoc);
			countGrpRegion.put(currentTime, countEntGrpRegion);

			// Cumulative treatment
			countGrpLoc = (HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP);

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

			// Cumulative treatment missed

			HashMap<Integer, int[]> countMap = (HashMap<Integer, int[]>) sim_output
					.get(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED);
			if (countMap == null) {
				countMap = new HashMap<>();
				sim_output.put(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED, countMap);
			}
			int[] ent = new int[NUM_INF * NUM_GRP];
			int pt = 0;
			if (cumul_treatment_missed_by_person != null) {
				for (int i = 0; i < NUM_INF; i++) {
					for (int g = 0; g < NUM_GRP; g++) {
						ent[pt] = cumul_treatment_missed_by_person[i][g];
						pt++;
					}
				}
			}
			countMap.put(currentTime, ent);

			countGrpLoc = (HashMap<Integer, int[][]>) sim_output.get(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED_BY_LOC_GRP);

			if (countGrpLoc == null) {
				countGrpLoc = new HashMap<>();
				sim_output.put(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED_BY_LOC_GRP, countGrpLoc);
			}

			int[][] export_cumul_treatment_missed = new int[NUM_GRP][location_name.length];
			if (cumul_treatment_missed_by_loc_grp != null) {
				for (int g = 0; g < export_cumul_treatment_missed.length; g++) {
					export_cumul_treatment_missed[g] = Arrays.copyOf(cumul_treatment_missed_by_loc_grp[g],
							location_name.length);
				}
			}
			countGrpLoc.put(currentTime, export_cumul_treatment_missed);

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
			printCountMap(countMap, fileName, "Inf_%d_Grp_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			countMap = (HashMap<Integer, int[]>) sim_output.get(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED);
			fileName = String.format(filePrefix + "Treatment_Person_Missed_%d_%d.csv", cMAP_SEED, sIM_SEED);
			printCountMap(countMap, fileName, "Inf_%d_Grp_%d", new int[] { NUM_INF, NUM_GRP }, COL_SEL_INF_GENDER);

			HashMap<Integer, int[][]> countGrpLoc;
			File file_grp_loc;
			countGrpLoc = (HashMap<Integer, int[][]>) sim_output.get(SIM_OUTPUT_KEY_CUMUL_TREATMENT_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Treatment_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);
			file_grp_loc = new File(seedFileDir, fileName);
			printGrpCount(countGrpLoc, file_grp_loc, location_name, "Grp_%d_Loc_%s");

			countGrpLoc = (HashMap<Integer, int[][]>) sim_output.get(SIM_OUTOUT_KEY_CUMUL_TREATMENT_MISSED_BY_LOC_GRP);
			fileName = String.format(filePrefix + "Treatment_Missed_by_GrpLoc_%d_%d.csv", cMAP_SEED, sIM_SEED);
			file_grp_loc = new File(seedFileDir, fileName);
			printGrpCount(countGrpLoc, file_grp_loc, location_name, "Grp_%d_Loc_%s");

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
			printGrpCount(countGrpLoc, file_grp_loc, location_name, "Grp_%d_Loc_%s");

			HashMap<Integer, int[][]> countGrpReg = (HashMap<Integer, int[][]>) sim_output
					.get(SIM_OUTPUT_KEY_PREVALENCE_BY_REG_GRP);
			fileName = String.format(filePrefix + "Infectious_by_GrpRegion_%d_%d.csv", cMAP_SEED, sIM_SEED);

			File file_grp_reg = new File(seedFileDir, fileName);
			printGrpCount(countGrpReg, file_grp_reg, region_name, "Grp_%d_%s");

		}

	}

	private void printGrpCount(HashMap<Integer, int[][]> countGrpLoc, File file_grp_loc, String[] subGrpName,
			String colHeaderFormat) {
		try {
			PrintWriter pri_grp_loc = new PrintWriter(file_grp_loc);
			StringBuilder header = new StringBuilder();
			header.append("Time");
			for (int g = 0; g < NUM_GRP; g++) {
				for (int sG = 0; sG < subGrpName.length; sG++) {
					header.append(',');
					header.append(String.format(colHeaderFormat, g, subGrpName[sG]));
				}
			}
			pri_grp_loc.println(header.toString());

			Integer[] time_arr = countGrpLoc.keySet().toArray(new Integer[0]);
			Arrays.sort(time_arr);

			for (int t : time_arr) {
				pri_grp_loc.print(t);
				int[][] ent = countGrpLoc.get(t);
				for (int g = 0; g < NUM_GRP; g++) {
					for (int sG = 0; sG < subGrpName.length; sG++) {
						pri_grp_loc.print(',');
						pri_grp_loc.print(ent[g][sG]);
					}
				}
				pri_grp_loc.println();
			}

			pri_grp_loc.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
		}
	}

	@Override
	protected double getTransmissionProb(int currentTime, int inf_id, int pid_inf_src, int pid_inf_tar,
			int partnershiptDur, int actType, int src_site, int tar_site) {		
		
		double[] actFieldEntry = table_act_frequency[actType][getPersonGrp(pid_inf_src)][getPersonGrp(pid_inf_tar)];

		double prob_tran = super.getTransmissionProb(currentTime, inf_id, pid_inf_src, pid_inf_tar, partnershiptDur,
				actType, src_site, tar_site);

		if (actFieldEntry.length > FIELD_ACT_FREQ_TRAN_ADJ_CITY) {
			prob_tran *= actFieldEntry[FIELD_ACT_FREQ_TRAN_ADJ_CITY
					+ getPersonRegion(pid_inf_src, indiv_map.get(pid_inf_src))];
		}

		return prob_tran;
	}
}
