package dev.underdeep.civ.econ;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure Stage 6 logic: bounded prices, no instant arbitrage, ledger integrity, transactions, routes, prosperity. */
class EconomyLogicTest {

    @Test
    void pricesRespondToStockAndStayBounded() {
        double scarce = Pricing.target(1, 0, 30, false, 0.3, 0.5, false, 1);
        double normal = Pricing.target(1, 30, 30, false, 0.3, 0.5, false, 1);
        double abundant = Pricing.target(1, 500, 30, true, 0.3, 0.5, false, 1);
        assertTrue(scarce > normal && normal > abundant, scarce + " > " + normal + " > " + abundant);
        assertTrue(scarce <= 3.0 && abundant >= 0.35);
        double food = Pricing.target(1, 0, 1000, false, 1, 1, true, 3);
        assertTrue(food <= 2.0, "essential food never above 2× base, got " + food);
        assertTrue(Pricing.target(1, 50, 30, true, 0, 0.5, false, 1) < Pricing.target(1, 50, 30, false, 0, 0.5, false, 1),
                "locally produced goods are cheaper than imports");
    }

    @Test
    void pricesMoveGradually() {
        double p = Pricing.approach(1.0, 2.0, 1);
        assertTrue(p > 1.0 && p < 1.3, "one hour moves only part of the way: " + p);
        assertEquals(2.0, Pricing.approach(1.0, 2.0, 200), 1e-6);
    }

    @Test
    void noInstantBuySellLoopAtAnyPrice() {
        for (double price = 0.01; price < 20; price *= 1.17) {
            for (double disc = 0; disc <= 0.12; disc += 0.04) {
                int[] sell = Pricing.sellBundle(Pricing.ask(price, 0.18, disc));
                int[] buy = Pricing.buyBundle(Pricing.bid(price, 0.18, disc * 0.4));
                double payPerUnit = (double) sell[0] / sell[1];
                double getPerUnit = (double) buy[1] / buy[0];
                assertTrue(getPerUnit <= payPerUnit + 1e-9, "price " + price + ": pay " + payPerUnit + ", get " + getPerUnit);
            }
        }
    }

    @Test
    void ledgerNeverGoesNegativeAndRespectsReservations() {
        Economy e = new Economy();
        e.stock("test:a").qty = 10;
        e.stock("test:a").reserved = 4;
        assertEquals(6, e.take("test:a", 100), 1e-9, "only free (unreserved) units can be taken");
        assertEquals(4, e.stock("test:a").qty, 1e-9);
        assertEquals(0, e.take("test:missing", 5), 1e-9);
        e.storageCapacity = 10;
        double in = e.put("test:b", 100);
        assertTrue(in <= 15 - 4 + 1e-9, "storage bounds production: " + in);
    }

    @Test
    void transactionsNeedFundsAndConserveMoney() {
        Economy.Household a = new Economy.Household(), b = new Economy.Household();
        a.purse = 5;
        assertFalse(Tx.pay(a, b, 6, 0, "test"), "cannot pay more than you have");
        assertTrue(Tx.pay(a, b, 3, 0, "test"));
        assertEquals(5, a.purse + b.purse, 1e-9);
        assertEquals(2, Tx.payUpTo(a, b, 10, 0, "test"), 1e-9);
        assertEquals(0, a.purse, 1e-9);
    }

    @Test
    void shipmentsMoveAlongTheirRoute() {
        Shipment s = new Shipment();
        s.route.add(new double[]{0, 10, 0});
        s.route.add(new double[]{100, 10, 0});
        s.route.add(new double[]{100, 20, 100});
        s.length = 200;
        s.progress = 150;
        s.status = Shipment.Status.TRAVELLING;
        double[] p = s.position();
        assertEquals(100, p[0], 1e-6);
        assertEquals(50, p[2], 1e-6);
        s.status = Shipment.Status.RETURNING;
        double[] q = s.position();
        assertEquals(50, q[0], 1e-6, "returning caravans walk the route backwards");
    }

    @Test
    void prosperityIsBoundedAndDisruptionLowersIt() {
        double[] v = new double[Prosperity.COUNT];
        java.util.Arrays.fill(v, 0.8);
        double calm = Prosperity.score(v, 0), troubled = Prosperity.score(v, 1);
        assertTrue(calm > troubled && calm <= 1 && troubled >= 0);
    }
}
