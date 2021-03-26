import com.sun.xml.internal.bind.v2.runtime.reflect.opt.Const;
import lia.api.*;
import lia.*;

import java.util.*;

/**
 * Initial implementation keeps picking random locations on the map
 * and sending units there. Worker units collect resources if they
 * see them while warrior units shoot if they see opponents.
 */
public class MyBot implements Bot {

    public List<UnitData> previousUpdateUnits = new ArrayList<UnitData>();
    public List<UnitData> guardBots = new ArrayList<UnitData>();


    // This method is called 10 times per game second and holds current
    // game state. Use Api object to call actions on your units.
    // - GameState reference: https://docs.liagame.com/api/#gamestate
    // - Api reference:       https://docs.liagame.com/api/#api-object
    @Override
    public void update(GameState state, Api api) {
        int numberOfWorkers = 0;
        List<OpponentInView> targetOpponents = new ArrayList<OpponentInView>();

        for (UnitData unit : state.units) {
            if (unit.type == UnitType.WORKER) numberOfWorkers++;
        }

        if (numberOfWorkers / (float) state.units.length < 0.5f) {
            if (state.resources >= Constants.WORKER_PRICE) {
                api.spawnUnit(UnitType.WORKER);
            }
        } else if (state.resources >= Constants.WARRIOR_PRICE) {
            api.spawnUnit(UnitType.WARRIOR);
        }

        RemoveDeadGuardBots(state.units);

        // We iterate through all of our units that are still alive.
        for (int i = 0; i < state.units.length; i++) {
            UnitData unit = state.units[i];

            // If the unit is a worker and it sees at least one resource
            // then make it go to the first resource to collect it.
            if (unit.type == UnitType.WORKER) {
                boolean anyOpponentIsLookingWorker = OpponentIsLooking(unit);
                boolean healthIsLower = HealthIsLower(unit, api);
                WorkerAction(unit, anyOpponentIsLookingWorker, api, healthIsLower);
            }

            // If the unit is a warrior and it sees an opponent then start shooting
            if (unit.type == UnitType.WARRIOR) {
                if (unit.opponentsInView.length > 0) {
                    OpponentInView opponent = DetermineOpponent(unit, targetOpponents);
                    targetOpponents.add(opponent);
                    float opponentAngle = GetOpponentAngle(unit, opponent);

                    WarriorAction(unit, opponent, opponentAngle, api, state);
                } else {
                    AssignGuardBot(unit);
                    MoveWarrior(unit, api, state);
                }
            }

            UpdatePreviousUnit(unit);
        }
    }

    private void MoveWarrior(UnitData unit, Api api, GameState state) {
        if (this.guardBots.stream().filter((guardBot) -> guardBot.id == unit.id).findFirst().isPresent()) {
            boolean bottomSpawn = Constants.SPAWN_POINT.y < (Constants.MAP_HEIGHT / 2);

            float distanceToCorner = bottomSpawn ? MathUtil.distance(unit.x, unit.y, 0, 0) : MathUtil.distance(unit.x, unit.y, Constants.MAP_WIDTH - 1, Constants.MAP_HEIGHT - 1);
            float lookDirection = bottomSpawn ? MathUtil.angleBetweenUnitAndPoint(unit, Constants.MAP_WIDTH - 1, Constants.MAP_HEIGHT - 1) : MathUtil.angleBetweenUnitAndPoint(unit, 0, 0);

            if (distanceToCorner > 6 && unit.speed == Speed.NONE) {
                Random random = new Random();
                int rngPos = random.nextInt(5);
                int xPos = Constants.SPAWN_POINT.x < (Constants.MAP_WIDTH / 2) ? 0 + rngPos : Constants.MAP_WIDTH - 1 - rngPos;
                int yPos = Constants.SPAWN_POINT.y < (Constants.MAP_HEIGHT / 2) ? 0 + rngPos : Constants.MAP_HEIGHT - 1 - rngPos;
                api.navigationStart(unit.id, xPos, yPos);
            } else if (Math.abs(lookDirection) > 10 && unit.speed == Speed.NONE) {
                api.setRotation(unit.id, Rotation.RIGHT);
            } else if (Math.abs(lookDirection) < 10) {
                api.navigationStop(unit.id);
                api.setSpeed(unit.id, Speed.NONE);
            }
        } else {
            float shortestDistance = 9999;
            UnitData workerToFollow = null;
            for (UnitData worker : state.units) {
                if (worker.type != UnitType.WORKER)
                    continue;

                float curDistance = MathUtil.distance(unit.x, unit.y, worker.x, worker.y);

                if (curDistance < shortestDistance)
                    workerToFollow = worker;
            }

            if (workerToFollow != null) {
                float xFollowPos = (workerToFollow.x + 3) > Constants.MAP_WIDTH ? workerToFollow.x - 3 : workerToFollow.x + 3;
                float yFollowPos = (workerToFollow.y + 3) > Constants.MAP_HEIGHT ? workerToFollow.y - 3 : workerToFollow.y + 3;
                if (!Constants.MAP[(int)xFollowPos][(int)yFollowPos]) {
                    xFollowPos = Constants.SPAWN_POINT.x;
                    yFollowPos = Constants.SPAWN_POINT.y;
                }


                api.navigationStart(unit.id, xFollowPos, yFollowPos);
            } else {
                while (true) {
                    int x = (int) (Math.random() * Constants.MAP_WIDTH);
                    int y = (int) (Math.random() * Constants.MAP_HEIGHT);

                    // Map is a 2D array of booleans. If map[x][y] equals false it means that
                    // at (x,y) there is no obstacle and we can safely move our unit there.
                    if (!Constants.MAP[x][y]) {
                        api.navigationStart(unit.id, x, y);
                        break;
                    }
                }
            }
        }
    }

    private void RemoveDeadGuardBots(UnitData[] units) {
        List<UnitData> removeGuards = new ArrayList<UnitData>();

        for (UnitData guardBot : this.guardBots) {
            Optional<UnitData> existingUnit = Arrays.stream(units).filter((currentUnit) -> currentUnit.id == guardBot.id).findFirst();

            if (!existingUnit.isPresent()) {
                removeGuards.add(guardBot);
            }
        }

        for (UnitData removeGuard : removeGuards) {
            this.guardBots.removeIf((guardBot) -> guardBot.id == removeGuard.id);
        }
    }

    private void AssignGuardBot(UnitData unit) {
        int currentUnitId = unit.id;
        UnitData existingUnit = this.guardBots.stream().filter((guardBot) -> guardBot.id == currentUnitId).findFirst().orElse(null);

        if (existingUnit == null && this.guardBots.size() < 2) {
            this.guardBots.add(unit);
        }
    }

    private boolean HealthIsLower(UnitData unit, Api api) {
        int currentUnitId = unit.id;
        boolean healthIsLower = false;

        Optional<UnitData> existingUnit = previousUpdateUnits.stream().filter((previousUpdateUnit) -> previousUpdateUnit.id == currentUnitId).findFirst();

        if (existingUnit.isPresent()) {
            healthIsLower = existingUnit.get().health > unit.health;

            if (healthIsLower) {
                api.saySomething(unit.id, "Health is " + String.valueOf(unit.health));
            }
        }

        return healthIsLower;
    }

    private void UpdatePreviousUnit(UnitData unit) {
        int currentUnitId = unit.id;

        previousUpdateUnits.removeIf((previousUpdateUnit) -> previousUpdateUnit.id == currentUnitId);

        previousUpdateUnits.add(unit);
    }

    private void WorkerAction(UnitData unit, boolean anyOpponentIsLookingWorker, Api api, boolean healthIsLower) {
        if (anyOpponentIsLookingWorker || healthIsLower) {
            api.saySomething(unit.id, "Run awaaayyy");
            api.navigationStart(unit.id, Constants.SPAWN_POINT.x, Constants.SPAWN_POINT.y, true);
        } else if (unit.resourcesInView.length > 0) {
            ResourceInView resource = unit.resourcesInView[0];
            api.navigationStart(unit.id, resource.x, resource.y);
        } else if (unit.navigationPath.length == 0) {
            while (true) {
                int x = (int) (Math.random() * Constants.MAP_WIDTH);
                int y = (int) (Math.random() * Constants.MAP_HEIGHT);

                // Map is a 2D array of booleans. If map[x][y] equals false it means that
                // at (x,y) there is no obstacle and we can safely move our unit there.
                if (!Constants.MAP[x][y]) {
                    api.navigationStart(unit.id, x, y);
                    break;
                }
            }
        }
    }

    private float GetOpponentAngle(UnitData unit, OpponentInView opponent) {
        UnitData opponentData = new UnitData(opponent.id, opponent.type, opponent.health, opponent.x, opponent.y, opponent.orientationAngle, opponent.speed, opponent.rotation, false, 0, new OpponentInView[0], new BulletInView[0], new ResourceInView[0], new Point[0]);

        return MathUtil.angleBetweenUnitAndPoint(opponentData, unit.x, unit.y);
    }

    private boolean OpponentIsLooking(UnitData unit) {
        boolean opponentIsLooking = false;

        for (int oppos = 0; oppos < unit.opponentsInView.length; oppos++) {
            OpponentInView curOppo = unit.opponentsInView[oppos];
            UnitData opponentData = new UnitData(curOppo.id, curOppo.type, curOppo.health, curOppo.x, curOppo.y, curOppo.orientationAngle, curOppo.speed, curOppo.rotation, false, 0, new OpponentInView[0], new BulletInView[0], new ResourceInView[0], new Point[0]);

            float oppoAngle = MathUtil.angleBetweenUnitAndPoint(opponentData, unit.x, unit.y);

            opponentIsLooking = curOppo.type == UnitType.WARRIOR && Math.abs(oppoAngle) < 15;

            if (opponentIsLooking) break;
        }

        return opponentIsLooking;
    }

    private OpponentInView DetermineOpponent(UnitData unit, List<OpponentInView> otherOpponents) {

        OpponentInView opponent = unit.opponentsInView[0];

        for (int oppos = 0; oppos < unit.opponentsInView.length; oppos++) {
            int curIterationId = unit.opponentsInView[oppos].id;

            Optional<OpponentInView> existingTarget = otherOpponents.stream().filter((opponentInView) -> opponentInView.id == curIterationId).findFirst();

            if (existingTarget.isPresent()) {
                opponent = existingTarget.get();
                break;
            }
        }


        return opponent;
    }

    private void WarriorAction(UnitData unit, OpponentInView opponent, float targetAngle, Api api, GameState state) {
        float aimAngle = MathUtil.angleBetweenUnitAndPoint(unit, opponent.x, opponent.y);
        api.navigationStop(unit.id);

        if (HealthIsLower(unit, api)) {
            api.setRotation(unit.id, Rotation.RIGHT);
        } else if (aimAngle < -5) {
            if (Math.abs(targetAngle) > 15) {
                api.setRotation(unit.id, Rotation.RIGHT);
            } else {
                api.setRotation(unit.id, Rotation.SLOW_RIGHT);
            }
        } else if (aimAngle > 5) {
            if (Math.abs(targetAngle) > 15) {
                api.setRotation(unit.id, Rotation.LEFT);
            } else {
                api.setRotation(unit.id, Rotation.SLOW_LEFT);
            }
        } else if (unit.canShoot) {
            boolean shouldShoot = true;
            for (UnitData otherUnit : state.units) {
                if (otherUnit.id == unit.id)
                    continue;

                shouldShoot = Math.abs(MathUtil.angleBetweenUnitAndPoint(unit, otherUnit.x, otherUnit.y)) > 5;
            }

            if (shouldShoot) {
                api.saySomething(unit.id, this.GetSomethingToSay());
                api.shoot(unit.id);
            }
        }
    }

    private String GetSomethingToSay() {
        Random rng = new Random();
        int rint = rng.nextInt(4);
        String output = "";

        switch (rint) {
            case 0:
                output = "Sorry, broke pipeline";
                break;
            case 1:
                output = "Nyt on kovaa koodia";
                break;
            case 2:
                output = "Wololooo";
                break;
            case 3:
                output = "Iffia ja hoelkynkoelkyn";
                break;
            case 4:
                output = "Game engine failed";
                break;
            default:
                break;
        }

        return output;
    }

    // Connects your bot to Lia game engine, don't change it.
    public static void main(String[] args) throws Exception {
        NetworkingClient.connectNew(args, new MyBot());
    }
}
