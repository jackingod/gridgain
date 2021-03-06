// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.resources;

import org.gridgain.grid.*;
import org.gridgain.grid.spi.*;
import java.lang.annotation.*;

/**
 * Annotates a field or a setter method for injection of resource
 * from Spring {@code ApplicationContext}. Use it whenever you would
 * like to inject resources specified in Spring application context of XML
 * configuration.
 * <p>
 * Logger can be injected into instances of following classes:
 * <ul>
 * <li>{@link GridTask}</li>
 * <li>{@link GridJob}</li>
 * <li>{@link GridSpi}</li>
 * <li>{@link GridLifecycleBean}</li>
 * <li>{@link GridUserResource @GridUserResource}</li>
 * </ul>
 * <p>
 * <h1 class="header">Resource Name</h1>
 * This is a mandatory parameter. Resource name will be used to access
 * Spring resources from Spring {@code ApplicationContext} or XML configuration.
 * <p>
 * Note that Spring resources cannot be peer-class-loaded. They must be available in
 * every {@code ApplicationContext} or Spring XML configuration on every grid node.
 * For this reason, if injected into a {@link java.io.Serializable} class, they must
 * be declared as {@code transient}.
 * <p>
 * The lifecycle of Spring resources is controlled by Spring container.
 * <p>
 * <h1 class="header">Examples</h1>
 * Here is how injection would typically happen:
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *      ...
 *      &#64;GridSpringResource(resourceName = "bean-name")
 *      private transient MyUserBean rsrc;
 *      ...
 *      &#64;GridUserResource
 *      private transient MyUserResource rsrc;
 *      ...
 *  }
 * </pre>
 * or
 * <pre name="code" class="java">
 * public class MyGridJob implements GridJob {
 *     ...
 *     private transient MyUserBean rsrc;
 *     ...
 *     &#64;GridSpringResource(resourceName = "bean-name")
 *     public void setMyUserBean(MyUserBean rsrc) {
 *          this.rsrc = rsrc;
 *     }
 *     ...
 * }
 * </pre>
 * and user resource {@code MyUserResource}
 * <pre name="code" class="java">
 * public class MyUserResource {
 *     ...
 *     &#64;GridSpringResource(resourceName = "bean-name")
 *     private MyUserBean rsrc;
 *     ...
 *     // Inject logger (or any other resource).
 *     &#64;GridLoggerResource
 *     private GridLogger log;
 *
 *     // Inject grid instance (or any other resource).
 *     &#64;GridInstanceResource
 *     private Grid grid;
 *     ...
 * }
 * </pre>
 * where spring bean resource class can look like this:
 * <pre name="code" class="java">
 * public class MyUserBean {
 *     ...
 * }
 * </pre>
 * and Spring file
 * <pre name="code" class="xml">
 * &lt;bean id="bean-name" class="my.foo.MyUserBean" singleton="true"&gt;
 *         ...
 * &lt;/bean&gt;
 * </pre>
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.09012012
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface GridSpringResource {
    /**
     * Resource bean name in provided {@code ApplicationContext} to look up
     * a Spring bean.
     *
     * @return Resource bean name.
     */
    String resourceName();
}
