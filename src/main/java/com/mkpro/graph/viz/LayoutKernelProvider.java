package org.graphify.viz.compute;

import org.jocl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.jocl.CL.*;

/**
 * Provides OpenCL context, command queue, and compiles the layout kernel.
 */
public class LayoutKernelProvider {
    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;
    private cl_device_id device;

    public LayoutKernelProvider() {
        initialize();
    }

    private void initialize() {
        final int platformIndex = 0;
        final long deviceType = CL_DEVICE_TYPE_ALL;
        final int deviceIndex = 0;

        CL.setExceptionsEnabled(true);

        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        int numPlatforms = numPlatformsArray[0];

        cl_platform_id[] platforms = new cl_platform_id[numPlatforms];
        clGetPlatformIDs(platforms.length, platforms, null);
        cl_platform_id platform = platforms[platformIndex];

        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);

        int[] numDevicesArray = new int[1];
        clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
        int numDevices = numDevicesArray[0];

        cl_device_id[] devices = new cl_device_id[numDevices];
        clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
        this.device = devices[deviceIndex];

        context = clCreateContext(contextProperties, 1, new cl_device_id[]{device}, null, null, null);
        
        cl_queue_properties properties = new cl_queue_properties();
        commandQueue = clCreateCommandQueueWithProperties(context, device, properties, null);

        String source = loadResource("/kernels/layout.cl");
        program = clCreateProgramWithSource(context, 1, new String[]{source}, null, null);
        
        try {
            clBuildProgram(program, 0, null, null, null, null);
        } catch (CLException e) {
            long[] size = new long[1];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, size);
            byte[] log = new byte[(int)size[0]];
            clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, log.length, Pointer.to(log), null);
            System.err.println("Kernel Build Error:\n" + new String(log));
            throw e;
        }
        
        kernel = clCreateKernel(program, "compute_forces", null);
    }

    private String loadResource(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Kernel resource not found: " + path);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load kernel source", e);
        }
    }

    public cl_context getContext() { return context; }
    public cl_command_queue getCommandQueue() { return commandQueue; }
    public cl_kernel getKernel() { return kernel; }

    public void cleanup() {
        if (kernel != null) clReleaseKernel(kernel);
        if (program != null) clReleaseProgram(program);
        if (commandQueue != null) clReleaseCommandQueue(commandQueue);
        if (context != null) clReleaseContext(context);
    }
}
