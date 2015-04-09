package com.github.atemerev.hollywood.johndoe;

import com.github.atemerev.hollywood.Actor;
import com.github.atemerev.hollywood.annotations.State;
import com.github.atemerev.hollywood.annotations.Mandatory;
import com.github.atemerev.hollywood.annotations.Initial;

/**
 * @author Alexander Temerev
 * @version $Id$
 */
public abstract class Clerk extends Actor {

    public abstract @Mandatory void talk();

    public abstract @Mandatory Integer getEnergyLevel();

    public abstract void goToJob();

    public abstract void goHome();

    public abstract void goToBar();

    public abstract void goToSleep();

    @Initial @State
    public static abstract class AtHome extends Clerk {

        private int sexualDesire = 120;

        public void talk() {
            System.out.println("Hey, gimme some beer!");
        }

        public int getSexualDesire() {
            return sexualDesire;
        }
    }

    @State
    public static abstract class AtWork extends Clerk {

        private int workAmount = 10;


        public void talk() {
            System.out.println("Where are my TPS reports?");
        }

        public int getWorkAmount() {
            return workAmount;
        }
    }

}
