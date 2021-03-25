import lia.api.*;
import lia.*;

import java.util.Random;

/**
 * Initial implementation keeps picking random locations on the map
 * and sending units there. Worker units collect resources if they
 * see them while warrior units shoot if they see opponents.
 */
public class MyBot implements Bot {


    // This method is called 10 times per game second and holds current
    // game state. Use Api object to call actions on your units.
    // - GameState reference: https://docs.liagame.com/api/#gamestate
    // - Api reference:       https://docs.liagame.com/api/#api-object
    @Override
    public void update(GameState state, Api api) {

        int numberOfWorkers = 0;
        for (UnitData unit : state.units) {
            if (unit.type == UnitType.WORKER) numberOfWorkers++;
        }

        if (numberOfWorkers / (float) state.units.length < 0.6f) {
            if (state.resources >= Constants.WORKER_PRICE) {
                api.spawnUnit(UnitType.WORKER);
            }
        } else if (state.resources >= Constants.WARRIOR_PRICE) {
            api.spawnUnit(UnitType.WARRIOR);
        }

        // We iterate through all of our units that are still alive.
        for (int i = 0; i < state.units.length; i++) {
            UnitData unit = state.units[i];

            // If the unit is not going anywhere, we send it
            // to a random valid location on the map.
            if (unit.navigationPath.length == 0) {

                // Generate new x and y until you get a position on the map
                // where there is no obstacle. Then move the unit there.
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

            // If the unit is a worker and it sees at least one resource
            // then make it go to the first resource to collect it.
            if (unit.type == UnitType.WORKER && unit.resourcesInView.length > 0) {
                ResourceInView resource = unit.resourcesInView[0];
                api.navigationStart(unit.id, resource.x, resource.y);
            }

            // If the unit is a warrior and it sees an opponent then start shooting
            if (unit.type == UnitType.WARRIOR && unit.opponentsInView.length > 0) {
                OpponentInView opponent = unit.opponentsInView[0];

                boolean opponentLooking = false;
                float oppoAngle = 180;
                for (int oppos = 0; oppos < unit.opponentsInView.length; oppos++) {
                    OpponentInView curOppo = unit.opponentsInView[oppos];
                    UnitData opponentData = new UnitData(curOppo.id, curOppo.type, curOppo.health, curOppo.x, curOppo.y, curOppo.orientationAngle, curOppo.speed, curOppo.rotation, false, 0, new OpponentInView[0], new BulletInView[0], new ResourceInView[0], new Point[0]);

                    oppoAngle = MathUtil.angleBetweenUnitAndPoint(opponentData, unit.x, unit.y);

                    opponentLooking = curOppo.type == UnitType.WARRIOR && oppoAngle < 5 && oppoAngle > -5;
                }


                float aimAngle = MathUtil.angleBetweenUnitAndPoint(unit, opponent.x, opponent.y);

                if (opponentLooking) {
                    api.saySomething(unit.id, "Run awaaayyy");
                    api.navigationStart(unit.id, Constants.SPAWN_POINT.x, Constants.SPAWN_POINT.y, true);
                } else if (aimAngle < -5) {
                    api.setRotation(unit.id, Rotation.RIGHT);
                } else if (aimAngle > 5) {
                    api.setRotation(unit.id, Rotation.LEFT);
                } else if (unit.canShoot) {
                    api.saySomething(unit.id, this.getSomethingToSay());
                    api.shoot(unit.id);
                }
            }
        }
    }

    public String getSomethingToSay() {
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
