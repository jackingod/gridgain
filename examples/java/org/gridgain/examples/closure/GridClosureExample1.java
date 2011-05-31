// Copyright (C) GridGain Systems, Inc. Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.closure;

import org.gridgain.grid.*;
import org.gridgain.grid.typedef.*;
import java.math.*;

/**
 * Demonstrates new functional APIs.
 *
 * @author 2005-2011 Copyright (C) GridGain Systems, Inc.
 * @version 3.1.0c.30052011
 * @see GridTaskExample1
 */
public class GridClosureExample1 {
    /**
     * Ensures singleton.
     */
    private GridClosureExample1() {
        /* No-op. */
    }

    /**
     * Execute factorial calculation example with closures.
     *
     * @param args Command line arguments, none required but if provided
     *      first one should point to the Spring XML configuration file. See
     *      {@code "examples/config/"} for configuration file examples.
     * @throws GridException If example execution failed.
     */
    public static void main(String[] args) throws Exception {
        // Typedefs:
        // ---------
        // G -> GridFactory
        // CIX1 -> GridInClosureX
        // CO -> GridOutClosure
        // CA -> GridAbsClosure
        // F -> GridFunc

        G.in(args.length == 0 ? null : args[0], new CIX1<Grid>() {
            @Override public void applyx(Grid g) throws GridException {
                // Initialise number for factorial calculation.
                final int num = 50;

                // Calculate factorial on local node with closure.
                // Example just demonstrates basic closure execution.
                BigInteger fact = g.localNode().call(new CO<BigInteger>() {
                    @Override public BigInteger apply() {
                        return GridNumberUtilExample.factorial(num);
                    }
                });

                // Print result.
                X.println(">>>>>");
                X.println(">>>>> Factorial for number " + num + " is " + fact);
                X.println(">>>>>");
            }
        });
    }
}