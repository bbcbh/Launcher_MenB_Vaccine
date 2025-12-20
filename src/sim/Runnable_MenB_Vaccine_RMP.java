package sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Pattern;

import relationship.ContactMap;

public class Runnable_MenB_Vaccine_RMP extends Runnable_MenB_Vaccine {

	public static final Pattern PROP_TYPE_PATTERN = Pattern.compile("ClusterModel_MenB_Vaccine_RMP");

	private static final int NUM_INF = 1; // NG
	private static final int NUM_SITE = 2; // 0 = Penile, 1=Vaginal
	private static final int NUM_ACT = 1; // Penile-vaginal sex only

	protected int vaccine_allocated_all_default_pt = 0;

	protected HashMap<Integer, double[]> current_vaccination_strategy_by_grp_inc = new HashMap<>();
	
	public Runnable_MenB_Vaccine_RMP(long cMap_seed, long sim_seed,  Properties prop) {
		super(cMap_seed, sim_seed, prop, NUM_INF, NUM_SITE, NUM_ACT);
		Arrays.sort(vaccine_allocate_all_default, new Comparator<double[]>() {
			@Override
			public int compare(double[] o1, double[] o2) {
				int res = 0;
				int pt = 0;
				while (res == 0 && pt < Math.min(o1.length, o2.length)) {
					res = Double.compare(o1[pt], o2[pt]);
					pt++;
				}
				return res;
			}
		});

		
		
	}

	protected void updateCurrentVaccinationStrategy(int currentTime) {
		while (vaccine_allocated_all_default_pt < vaccine_allocate_all_default.length
				&& vaccine_allocate_all_default[vaccine_allocated_all_default_pt][VACCINE_ALLOCATE_GLOBAL_START] <= currentTime) {

			int grpInc = (int) (vaccine_allocate_all_default[vaccine_allocated_all_default_pt][VACCINE_ALLOCATE_GRP_INC]);

			current_vaccination_strategy_by_grp_inc.put(grpInc,
					Arrays.copyOfRange(vaccine_allocate_all_default[vaccine_allocated_all_default_pt],
							VACCINE_ALLOCATE_GRP_INC + 1,
							vaccine_allocate_all_default[vaccine_allocated_all_default_pt].length));
			vaccine_allocated_all_default_pt++;
		}

	}	

	
	@Override
	protected void testPerson(int currentTime, int pid_t, int infIncl, int siteIncl,
			int[][] cumul_treatment_by_person) {

		super.testPerson(currentTime, pid_t, infIncl, siteIncl, cumul_treatment_by_person);
		int pid = Math.abs(pid_t);

		if (!vaccination_history.containsKey(pid)) {
			int grp = getPersonGrp(pid);
			for (Integer grpInc : current_vaccination_strategy_by_grp_inc.keySet()) {
				if ((grpInc.intValue() & 1 << grp) != 0) {
					double[] current_vaccine_allocation = current_vaccination_strategy_by_grp_inc.get(grpInc);

					// PROB_DOSE_0_AT_TEST,PROB_NEXT_DOSE_0,NEXT_DOSE_AT_0,PROB_NEXT_DOSE_1,
					// NEXT_DOSE_AT_1,..
					double pDoseAtTest = current_vaccine_allocation[0];
					if (rng_vaccine.nextDouble() < pDoseAtTest) {
						// First dose
						ArrayList<Integer> vac_hist = new ArrayList<>();
						vac_hist.add(currentTime);
						vaccination_history.put(pid, vac_hist);

						// Check for booster

						boolean boosterEnd = false;

						for (int booster_prob_index = 2; booster_prob_index < current_vaccine_allocation.length
								&& !boosterEnd; booster_prob_index += 2) {

							boosterEnd = !(rng_vaccine.nextDouble() < current_vaccine_allocation[booster_prob_index]);
							if (!boosterEnd) {
								double mean_booster_schedule = current_vaccine_allocation[booster_prob_index + 1];
								int booster_time = currentTime + (int) Math.round(mean_booster_schedule);

								ArrayList<Integer> booster_pid = schedule_booster.get(booster_time);
								if (booster_pid == null) {
									booster_pid = new ArrayList<>();
									schedule_booster.put(booster_time, booster_pid);
								}
								booster_pid.add(pid);

							}

						}
					}
				}
			}

		}

	}

	@Override
	protected void postTimeStep(int currentTime) {
		super.postTimeStep(currentTime);
		// Preset for next turn
		if (currentTime != 0) {
			int nextTime = currentTime + 1;
			updateCurrentVaccinationStrategy(nextTime);
			if (schedule_booster.containsKey(nextTime)) {
				ArrayList<Integer> booster_pid = schedule_booster.remove(nextTime);
				for (Integer pid : booster_pid) {
					vaccination_history.get(pid).add(nextTime);
				}
			}
		}

		// TODO: Others steps
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void postSimulation() {
		// TODO Auto-generated method stub
		super.postSimulation();
	}

}
