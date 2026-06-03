package org.graphify.viz.compute;

import org.jocl.*;
import static org.jocl.CL.*;

import java.util.logging.Logger;

/**
 * Manages OpenCL resources including platform, device, context, and command queue.
 */
public class JOCLResourceManager {
    private static final Logger logger = Logger.getLogger(JOCLResourceManager.class.getName());

    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_device_id device;

    public JOCLResourceManager() {
        initialize();
    }

    private void initialize() {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        CL.setExceptionsEnabled(true);

        // Obtain the number of platforms
        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        // Obtain a platform ID
        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        // Initialize context properties
        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        // Obtain the number of devices for the platform
        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        // Obtain a device ID
        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        device = devices[deviceIndex];

        // Create a context for the selected device
        context = clCreateContext(
            contextProperties, 1, new cl_device_id[]{device}, 
            null, null, null);

        // Create a command-queue for the selected device
        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = clCreateCommandQueueWithProperties(
            context, device, properties, null);
        
        logger.info("JOCL Resource Manager initialized successfully.");
    }

    public cl_context getContext() {
        return context;
    }

    public cl_command_queue getCommandQueue() {
        return commandQueue;
    }

    public cl_device_id getDevice() {
        return device;
    }

    public void cleanup() {
        if (commandQueue != null) {
            clReleaseCommandQueue(commandQueue);
        }
        if (context != null) {
            clReleaseContext(context);
        }
    }
}
