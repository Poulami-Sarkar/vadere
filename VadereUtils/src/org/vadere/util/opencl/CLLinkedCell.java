package org.vadere.util.opencl;


import org.vadere.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CLContextCallback;
import org.lwjgl.opencl.CLProgramCallback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.vadere.util.geometry.shapes.VPoint;
import org.vadere.util.geometry.shapes.VRectangle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opencl.CL10.CL_CONTEXT_PLATFORM;
import static org.lwjgl.opencl.CL10.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.lwjgl.opencl.CL10.CL_DEVICE_NAME;
import static org.lwjgl.opencl.CL10.CL_DEVICE_TYPE_GPU;
import static org.lwjgl.opencl.CL10.CL_MEM_ALLOC_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_COPY_HOST_PTR;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_WRITE;
import static org.lwjgl.opencl.CL10.CL_PROGRAM_BUILD_STATUS;
import static org.lwjgl.opencl.CL10.CL_SUCCESS;
import static org.lwjgl.opencl.CL10.clBuildProgram;
import static org.lwjgl.opencl.CL10.clCreateBuffer;
import static org.lwjgl.opencl.CL10.clCreateCommandQueue;
import static org.lwjgl.opencl.CL10.clCreateContext;
import static org.lwjgl.opencl.CL10.clCreateKernel;
import static org.lwjgl.opencl.CL10.clCreateProgramWithSource;
import static org.lwjgl.opencl.CL10.clEnqueueNDRangeKernel;
import static org.lwjgl.opencl.CL10.clEnqueueReadBuffer;
import static org.lwjgl.opencl.CL10.clEnqueueWriteBuffer;
import static org.lwjgl.opencl.CL10.clFinish;
import static org.lwjgl.opencl.CL10.clGetDeviceIDs;
import static org.lwjgl.opencl.CL10.clGetDeviceInfo;
import static org.lwjgl.opencl.CL10.clGetPlatformIDs;
import static org.lwjgl.opencl.CL10.clReleaseCommandQueue;
import static org.lwjgl.opencl.CL10.clReleaseContext;
import static org.lwjgl.opencl.CL10.clReleaseKernel;
import static org.lwjgl.opencl.CL10.clReleaseMemObject;
import static org.lwjgl.opencl.CL10.clReleaseProgram;
import static org.lwjgl.opencl.CL10.clSetKernelArg;
import static org.lwjgl.opencl.CL10.clSetKernelArg1i;
import static org.lwjgl.opencl.CL10.clSetKernelArg1p;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memUTF8;

/**
 * @author Benedikt Zoennchen
 *
 * This class offers the methods to compute an array based linked-cell which contains 2D-coordinates i.e. {@link VPoint}
 * using the GPU (see. green-2007 Building the Grid using Sorting).
 */
public class CLLinkedCell {
	private static Logger log = Logger.getLogger(CLLinkedCell.class);

	// CL ids
	private long clPlatform;
	private long clDevice;
	private long clContext;
	private long clQueue;
	private long clProgram;

	// CL Memory
	private long clHashes;
	private long clIndices;
	private long clCellStarts;
	private long clCellEnds;
	private long clReorderedPositions;
	private long clPositions;
	private long clCellSize;
	private long clWorldOrigin;
	private long clGridSize;

	// Host Memory
	private IntBuffer hashes;
	private IntBuffer indices;
	private IntBuffer cellStarts;
	private IntBuffer cellEnds;
	private FloatBuffer reorderedPositions;
	private FloatBuffer positions;
	private FloatBuffer worldOrigin;
	private FloatBuffer cellSize;
	private IntBuffer gridSize;


	private IntBuffer inValues;
	private IntBuffer outValues;

	private ByteBuffer source;
	private ByteBuffer particleSource;

	// CL callbacks
	private CLContextCallback contextCB;
	private CLProgramCallback programCB;

	// CL kernel
	private long clBitonicSortLocal;
	private long clBitonicSortLocal1;
	private long clBitonicMergeGlobal;
	private long clBitonicMergeLocal;
	private long clCalcHash;
	private long clFindCellBoundsAndReorder;

	private int numberOfElements;
	private int numberOfGridCells;
	private VRectangle bound;
	private float iCellSize;
	private int[] iGridSize;
	private List<VPoint> positionList;

	private int[] keys;
	private int[] values;

	private int[] resultValues;
	private int[] resultKeys;

	private static final Logger logger = Logger.getLogger(CLLinkedCell.class);

	private int max_work_group_size;

	private boolean debug = false;

	public enum KernelType {
		Separate,
		Col,
		Row,
		NonSeparate
	}

	/**
	 * Default constructor.
	 *
	 * @param numberOfElements  the number of positions contained in the linked cell.
	 * @param bound             the spatial bound of the linked cell.
	 * @param cellSize          the cellSize (in x and y direction) of the linked cell.
	 *
	 * @throws OpenCLException
	 */
	public CLLinkedCell(final int numberOfElements, final VRectangle bound, final double cellSize) throws OpenCLException {
		this.numberOfElements = numberOfElements;
		this.iGridSize = new int[]{ (int)Math.ceil(bound.getWidth() / cellSize),  (int)Math.ceil(bound.getHeight() / cellSize)};
		this.numberOfGridCells = this.iGridSize[0] * this.iGridSize[1];
		this.bound = bound;
		this.iCellSize = (float)cellSize;

		if(debug) {
			Configuration.DEBUG.set(true);
			Configuration.DEBUG_MEMORY_ALLOCATOR.set(true);
			Configuration.DEBUG_STACK.set(true);
		}
		init();
	}

	/**
	 * The data structure representing the linked cell. The elements of cell i
	 * between (reorderedPositions[cellStart[i]*2], reorderedPositions[cellStart[i]*2+1])
	 * and (reorderedPositions[(cellEnds[i]-1)*2], reorderedPositions[(cellEnds[i]-1)*2+1]).
	 */
	public class LinkedCell {
		/**
		 * the starting index at which the cell starts, i.e. cell i starts at cellStart[i].
		 */
		public int[] cellStarts;

		/**
		 * the ending index at which the cell starts, i.e. cell i ends at cellStart[i].
		 */
		public int[] cellEnds;

		/**
		 * the ordered 2D-coordinates.
		 */
		public float[] reorderedPositions;

		/**
		 * the mapping between the unordered (original) positions and the reorderedPositions,
		 * i.e. reorderedPositions[i] == positions[indices[i]]
		 */
		public int[] indices;

		/**
		 * the hashes i.e. the cell of the positions, i.e. hashes[i] is the cell of positions[i].
		 */
		public int[] hashes;

		/**
		 * the original positions in original order.
		 */
		public float[] positions;
	}

	/**
	 * Computes the {@link LinkedCell} of the list of positions.
	 *
	 * @param positions a list of position contained in {@link CLLinkedCell#bound}.
	 * @return {@link LinkedCell} which is the linked list in an array based structure.
	 *
	 * @throws OpenCLException
	 */
	public LinkedCell calcLinkedCell(@NotNull final List<VPoint> positions) throws OpenCLException {
		assert positions.size() == numberOfElements;
		this.positionList = positions;
		allocHostMemory();
		allocDeviceMemory();

		clCalcHash(clHashes, clIndices, clPositions, clCellSize, clWorldOrigin, clGridSize, numberOfElements);
		clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfElements, 1);
		clFindCellBoundsAndReorder(clCellStarts, clCellEnds, clReorderedPositions, clHashes, clIndices, clPositions, numberOfElements);

		clEnqueueReadBuffer(clQueue, clCellStarts, true, 0, cellStarts, null, null);
		clEnqueueReadBuffer(clQueue, clCellEnds, true, 0, cellEnds, null, null);
		clEnqueueReadBuffer(clQueue, clReorderedPositions, true, 0, reorderedPositions, null, null);
		clEnqueueReadBuffer(clQueue, clIndices, true, 0, indices, null, null);
		clEnqueueReadBuffer(clQueue, clHashes, true, 0, hashes, null, null);
		clEnqueueReadBuffer(clQueue, clPositions, true, 0, this.positions, null, null);

		int[] aCellStarts = CLUtils.toIntArray(cellStarts, numberOfGridCells);
		int[] aCellEnds = CLUtils.toIntArray(cellEnds, numberOfGridCells);
		float[] aReorderedPositions = CLUtils.toFloatArray(reorderedPositions, numberOfElements * 2);
		int[] aIndices = CLUtils.toIntArray(indices, numberOfElements);
		int[] aHashes = CLUtils.toIntArray(hashes, numberOfElements);
		float[] aPositions = CLUtils.toFloatArray(this.positions, numberOfElements * 2);

		LinkedCell gridCells = new LinkedCell();
		gridCells.cellEnds = aCellEnds;
		gridCells.cellStarts = aCellStarts;
		gridCells.reorderedPositions = aReorderedPositions;
		gridCells.indices = aIndices;
		gridCells.hashes = aHashes;
		gridCells.positions = aPositions;

		clearMemory();
		clearCL();

		return gridCells;
		//clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfElements, 1);
		//clFindCellBoundsAndReorder(clCellStarts, clCellEnds, clReorderedPositions, clHashes, clIndices, clPositions, numberOfElements, numberOfGridCells);
	}

	/**
	 * Computes all the hash values, i.e. cells of each position and sort these hashes and construct a mapping
	 * of the rearrangement. This method exists to test the bitonic sort algorithm on the GPU.
	 *
	 * @param positions the positions which will be hashed.
	 * @return  the sorted hashes.
	 * @throws OpenCLException
	 */
	public int[] calcSortedHashes(@NotNull final List<VPoint> positions) throws OpenCLException {
		assert positions.size() == numberOfElements;
		this.positionList = positions;
		allocHostMemory();
		allocDeviceMemory();

		clCalcHash(clHashes, clIndices, clPositions, clCellSize, clWorldOrigin, clGridSize, numberOfElements);
		clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfElements, 1);
		clEnqueueReadBuffer(clQueue, clHashes, true, 0, hashes, null, null);
		int[] result = CLUtils.toIntArray(hashes, numberOfElements);

		clearMemory();
		clearCL();
		return result;

		//clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfElements, 1);
		//clFindCellBoundsAndReorder(clCellStarts, clCellEnds, clReorderedPositions, clHashes, clIndices, clPositions, numberOfElements, numberOfGridCells);
	}

	/**
	 * Computes all the hash values, i.e. cells of each position.
	 * This method exists to test the hash computation on the GPU.
	 *
	 * @param positions the positions which will be hashed.
	 * @return the (unsorted) hashes.
	 * @throws OpenCLException
	 */
	public int[] calcHashes(@NotNull final List<VPoint> positions) throws OpenCLException {
		assert positions.size() == numberOfElements;
		this.positionList = positions;
		allocHostMemory();
		allocDeviceMemory();

		clCalcHash(clHashes, clIndices, clPositions, clCellSize, clWorldOrigin, clGridSize, numberOfElements);
		clEnqueueReadBuffer(clQueue, clHashes, true, 0, hashes, null, null);
		int[] result = CLUtils.toIntArray(hashes, numberOfElements);

		clearMemory();
		clearCL();
		return result;

		//clBitonicSort(clHashes, clIndices, clHashes, clIndices, numberOfElements, 1);
		//clFindCellBoundsAndReorder(clCellStarts, clCellEnds, clReorderedPositions, clHashes, clIndices, clPositions, numberOfElements, numberOfGridCells);
	}

	/**
	 * Returns the gridSizes of the linked cell, i.e. result[0] is the x and
	 * result[1] the y direction.
	 *
	 * @return the gridSizes (2D) stored in an array.
	 */
	public int[] getGridSize() {
		return new int[]{iGridSize[0], iGridSize[1]};
	}

	/**
	 * Returns the gridSize which is equal in x and y direction.
	 *
	 * @return the gridSize
	 */
	public float getCellSize() {
		return iCellSize;
	}

	public VPoint getWorldOrign() {
		return new VPoint(bound.getMinX(), bound.getMinY());
	}

	public void allocHostMemory() {
		assert positionList.size() == numberOfElements;
		float[] pos = new float[numberOfElements*2];
		for(int i = 0; i < numberOfElements; i++) {
			pos[i*2] = (float)positionList.get(i).getX();
			pos[i*2+1] = (float)positionList.get(i).getY();
		}
		this.positions = CLUtils.toFloatBuffer(pos, CLUtils.toFloatBuffer(pos));
		this.hashes = MemoryUtil.memAllocInt(numberOfElements);

		float[] originArray = new float[]{(float)bound.getMinX(), (float)bound.getMinX()};
		this.worldOrigin = CLUtils.toFloatBuffer(originArray, CLUtils.toFloatBuffer(originArray));

		this.cellSize = MemoryUtil.memAllocFloat(1);
		this.cellSize.put(0, iCellSize);

		this.gridSize = CLUtils.toIntBuffer(iGridSize, CLUtils.toIntBuffer(iGridSize));

		this.cellStarts = MemoryUtil.memAllocInt(numberOfGridCells);
		this.cellEnds = MemoryUtil.memAllocInt(numberOfGridCells);
		this.indices = MemoryUtil.memAllocInt(numberOfElements);
		this.reorderedPositions = MemoryUtil.memAllocFloat(numberOfElements * 2);
	}

	private void allocDeviceMemory() {
		try (MemoryStack stack = stackPush()) {
			IntBuffer errcode_ret = stack.callocInt(1);
			clCellSize = clCreateBuffer(clContext,  CL_MEM_READ_ONLY | CL_MEM_ALLOC_HOST_PTR | CL_MEM_COPY_HOST_PTR, cellSize, errcode_ret);
			clWorldOrigin = clCreateBuffer(clContext,  CL_MEM_READ_ONLY | CL_MEM_ALLOC_HOST_PTR | CL_MEM_COPY_HOST_PTR, worldOrigin, errcode_ret);
			clGridSize = clCreateBuffer(clContext,  CL_MEM_READ_ONLY | CL_MEM_ALLOC_HOST_PTR | CL_MEM_COPY_HOST_PTR, gridSize, errcode_ret);
			clHashes = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * numberOfElements, errcode_ret);
			clIndices = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * numberOfElements, errcode_ret);
			clCellStarts = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * numberOfGridCells, errcode_ret);
			clCellEnds = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 4 * numberOfGridCells, errcode_ret);
			clReorderedPositions = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 2 * 4 * numberOfElements, errcode_ret);
			clPositions = clCreateBuffer(clContext, CL_MEM_READ_WRITE, 2 * 4 * numberOfElements, errcode_ret);
			clEnqueueWriteBuffer(clQueue, clPositions, true, 0, positions, null, null);
		}
	}

	public int[] getResultKeys() {
		return resultKeys;
	}

	public int[] getResultValues() {
		return resultValues;
	}

	private void init() throws OpenCLException {
		initCallbacks();
		initCL();
		buildProgram();
	}

	private void clCalcHash(
			final long clHashes,
			final long clIndices,
			final long clPositions,
			final long clCellSize,
			final long clWorldOrign,
			final long clGridSize,
			final int numberOfElements) throws OpenCLException {
		try (MemoryStack stack = stackPush()) {
			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 0, clHashes));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 1, clIndices));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 2, clPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 3, clCellSize));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 4, clWorldOrign));
			CLInfo.checkCLError(clSetKernelArg1p(clCalcHash, 5, clGridSize));
			CLInfo.checkCLError(clSetKernelArg1i(clCalcHash, 6, numberOfElements));
			clGlobalWorkSize.put(0, numberOfElements);
			//TODO: local work size?
			CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clCalcHash, 1, null, clGlobalWorkSize, null, null, null));
		}
	}

	private void clFindCellBoundsAndReorder(
			final long clCellStarts,
			final long clCellEnds,
			final long clReorderedPositions,
			final long clHashes,
			final long clIndices,
			final long clPositions,
			final int numberOfElements) throws OpenCLException {

		try (MemoryStack stack = stackPush()) {

			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			PointerBuffer clLocalWorkSize = stack.callocPointer(1);
			IntBuffer errcode_ret = stack.callocInt(1);

			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 0, clCellStarts));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 1, clCellEnds));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 2, clReorderedPositions));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 3, clHashes));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 4, clIndices));
			CLInfo.checkCLError(clSetKernelArg1p(clFindCellBoundsAndReorder, 5, clPositions));
			CLInfo.checkCLError(clSetKernelArg(clFindCellBoundsAndReorder, 6, (Math.min(numberOfElements, max_work_group_size)+1) * 4)); // local memory
			CLInfo.checkCLError(clSetKernelArg1i(clFindCellBoundsAndReorder, 7, numberOfElements));


			int globalWorkSize;
			int localWorkSize;
			if(numberOfElements <= max_work_group_size){
				localWorkSize = numberOfElements;
				globalWorkSize = numberOfElements;
			}
			else {
				localWorkSize = max_work_group_size;
				globalWorkSize = multipleOf(numberOfElements, localWorkSize);
			}

			clGlobalWorkSize.put(0, globalWorkSize);
			clLocalWorkSize.put(0, localWorkSize);
			//TODO: local work size? + check 2^n constrain!
			CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clFindCellBoundsAndReorder, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
		}
	}

	private int multipleOf(int value, int multiple) {
		int result = multiple;
		while (result < value) {
			result += multiple;
		}
		return result;
	}

	private void clBitonicSort(
			final long clKeysIn,
			final long clValuesIn,
			final long clKeysOut,
			final long clValuesOut,
			final int numberOfElements,
			final int dir) throws OpenCLException {
		try (MemoryStack stack = stackPush()) {

			PointerBuffer clGlobalWorkSize = stack.callocPointer(1);
			PointerBuffer clLocalWorkSize = stack.callocPointer(1);
			IntBuffer errcode_ret = stack.callocInt(1);

			// small sorts
			if (numberOfElements <= max_work_group_size) {
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal, 0, clKeysOut));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal, 1, clValuesOut));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal, 2, clKeysIn));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal, 3, clValuesIn));
				CLInfo.checkCLError(clSetKernelArg1i(clBitonicSortLocal, 4, numberOfElements));
				CLInfo.checkCLError(clSetKernelArg1i(clBitonicSortLocal, 5, dir));
				CLInfo.checkCLError(clSetKernelArg(clBitonicSortLocal, 6, numberOfElements * 4)); // local memory
				CLInfo.checkCLError(clSetKernelArg(clBitonicSortLocal, 7, numberOfElements * 4)); // local memory
				clGlobalWorkSize.put(0, numberOfElements / 2);
				clLocalWorkSize.put(0, numberOfElements / 2);

				// run the kernel and read the result
				CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clBitonicSortLocal, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
				CLInfo.checkCLError(clFinish(clQueue));
			} else {
				//Launch bitonicSortLocal1
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal1, 0, clKeysOut));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal1, 1, clValuesOut));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal1, 2, clKeysIn));
				CLInfo.checkCLError(clSetKernelArg1p(clBitonicSortLocal1, 3, clValuesIn));
				CLInfo.checkCLError(clSetKernelArg(clBitonicSortLocal1, 4, max_work_group_size * 4)); // local memory
				CLInfo.checkCLError(clSetKernelArg(clBitonicSortLocal1, 5, max_work_group_size * 4)); // local memory

				clGlobalWorkSize = stack.callocPointer(1);
				clLocalWorkSize = stack.callocPointer(1);
				clGlobalWorkSize.put(0, numberOfElements / 2);
				clLocalWorkSize.put(0, max_work_group_size / 2);

				CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clBitonicSortLocal1, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
				CLInfo.checkCLError(clFinish(clQueue));

				for (int size = 2 * max_work_group_size; size <= numberOfElements; size <<= 1) {
					for (int stride = size / 2; stride > 0; stride >>= 1) {
						if (stride >= max_work_group_size) {
							//Launch bitonicMergeGlobal
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeGlobal, 0, clKeysOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeGlobal, 1, clValuesOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeGlobal, 2, clKeysOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeGlobal, 3, clValuesOut));

							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeGlobal, 4, numberOfElements));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeGlobal, 5, size));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeGlobal, 6, stride));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeGlobal, 7, dir));

							clGlobalWorkSize = stack.callocPointer(1);
							clLocalWorkSize = stack.callocPointer(1);
							clGlobalWorkSize.put(0, numberOfElements / 2);
							clLocalWorkSize.put(0, max_work_group_size / 4);

							CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clBitonicMergeGlobal, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
							CLInfo.checkCLError(clFinish(clQueue));
						} else {
							//Launch bitonicMergeLocal
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeLocal, 0, clKeysOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeLocal, 1, clValuesOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeLocal, 2, clKeysOut));
							CLInfo.checkCLError(clSetKernelArg1p(clBitonicMergeLocal, 3, clValuesOut));

							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeLocal, 4, numberOfElements));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeLocal, 5, stride));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeLocal, 6, size));
							CLInfo.checkCLError(clSetKernelArg1i(clBitonicMergeLocal, 7, dir));
							CLInfo.checkCLError(clSetKernelArg(clBitonicMergeLocal, 8, max_work_group_size * 4)); // local memory
							CLInfo.checkCLError(clSetKernelArg(clBitonicMergeLocal, 9, max_work_group_size * 4)); // local memory

							clGlobalWorkSize = stack.callocPointer(1);
							clLocalWorkSize = stack.callocPointer(1);
							clGlobalWorkSize.put(0, numberOfElements / 2);
							clLocalWorkSize.put(0, max_work_group_size / 2);

							CLInfo.checkCLError(clEnqueueNDRangeKernel(clQueue, clBitonicMergeLocal, 1, null, clGlobalWorkSize, clLocalWorkSize, null, null));
							CLInfo.checkCLError(clFinish(clQueue));
							break;
						}
					}
				}
			}
		}
	}

	static long factorRadix2(long L){
		if(L==0){
			return 0;
		}else{
			for(int log2L = 0; (L & 1) == 0; L >>= 1, log2L++);
			return L;
		}
	}

	public void clear() throws OpenCLException {
		clearMemory();
	}

	private void clearMemory() throws OpenCLException {
		// release memory and devices
		try {
			CLInfo.checkCLError(clReleaseMemObject(clHashes));
			CLInfo.checkCLError(clReleaseMemObject(clIndices));
			CLInfo.checkCLError(clReleaseMemObject(clCellStarts));
			CLInfo.checkCLError(clReleaseMemObject(clCellEnds));
			CLInfo.checkCLError(clReleaseMemObject(clReorderedPositions));
			CLInfo.checkCLError(clReleaseMemObject(clPositions));
			CLInfo.checkCLError(clReleaseMemObject(clCellSize));
			CLInfo.checkCLError(clReleaseMemObject(clWorldOrigin));
			CLInfo.checkCLError(clReleaseMemObject(clGridSize));
		}
		catch (OpenCLException ex) {
			throw ex;
		}
		finally {
			MemoryUtil.memFree(hashes);
			MemoryUtil.memFree(indices);
			MemoryUtil.memFree(cellStarts);
			MemoryUtil.memFree(cellEnds);
			MemoryUtil.memFree(reorderedPositions);
			MemoryUtil.memFree(positions);
			MemoryUtil.memFree(worldOrigin);
			MemoryUtil.memFree(cellSize);
			MemoryUtil.memFree(gridSize);
		}
	}

	private void clearCL() throws OpenCLException {
		CLInfo.checkCLError(clReleaseKernel(clBitonicSortLocal));
		CLInfo.checkCLError(clReleaseKernel(clBitonicSortLocal1));
		CLInfo.checkCLError(clReleaseKernel(clBitonicMergeGlobal));
		CLInfo.checkCLError(clReleaseKernel(clBitonicMergeLocal));
		CLInfo.checkCLError(clReleaseKernel(clCalcHash));
		CLInfo.checkCLError(clReleaseKernel(clFindCellBoundsAndReorder));

		CLInfo.checkCLError(clReleaseCommandQueue(clQueue));
		CLInfo.checkCLError(clReleaseProgram(clProgram));
		CLInfo.checkCLError(clReleaseContext(clContext));
		contextCB.free();
		programCB.free();
	}

	// private helpers
	private void initCallbacks() {
		contextCB = CLContextCallback.create((errinfo, private_info, cb, user_data) ->
		{
			log.debug("[LWJGL] cl_context_callback" + "\tInfo: " + memUTF8(errinfo));
		});

		programCB = CLProgramCallback.create((program, user_data) ->
		{
			try {
				log.debug("The cl_program [0x"+program+"] was built " + (CLInfo.getProgramBuildInfoInt(program, clDevice, CL_PROGRAM_BUILD_STATUS) == CL_SUCCESS ? "successfully" : "unsuccessfully"));
			} catch (OpenCLException e) {
				e.printStackTrace();
			}
		});
	}

	private void initCL() throws OpenCLException {
		try (MemoryStack stack = stackPush()) {
			IntBuffer errcode_ret = stack.callocInt(1);
			IntBuffer numberOfPlatforms = stack.mallocInt(1);

			CLInfo.checkCLError(clGetPlatformIDs(null, numberOfPlatforms));
			PointerBuffer platformIDs = stack.mallocPointer(numberOfPlatforms.get(0));
			CLInfo.checkCLError(clGetPlatformIDs(platformIDs, numberOfPlatforms));

			clPlatform = platformIDs.get(0);

			IntBuffer numberOfDevices = stack.mallocInt(1);
			CLInfo.checkCLError(clGetDeviceIDs(clPlatform, CL_DEVICE_TYPE_GPU, null, numberOfDevices));
			PointerBuffer deviceIDs = stack.mallocPointer(numberOfDevices.get(0));
			CLInfo.checkCLError(clGetDeviceIDs(clPlatform, CL_DEVICE_TYPE_GPU, deviceIDs, numberOfDevices));

			clDevice = deviceIDs.get(0);

			log.debug("CL_DEVICE_NAME = " + CLInfo.getDeviceInfoStringUTF8(clDevice, CL_DEVICE_NAME));

			PointerBuffer ctxProps = stack.mallocPointer(3);
			ctxProps.put(CL_CONTEXT_PLATFORM)
					.put(clPlatform)
					.put(NULL)
					.flip();

			clContext = clCreateContext(ctxProps, clDevice, contextCB, NULL, errcode_ret);
			CLInfo.checkCLError(errcode_ret);

			clQueue = clCreateCommandQueue(clContext, clDevice, 0, errcode_ret);
			CLInfo.checkCLError(errcode_ret);
		}
	}

	private void buildProgram() throws OpenCLException {
		try (MemoryStack stack = stackPush()) {
			IntBuffer errcode_ret = stack.callocInt(1);

			PointerBuffer strings = stack.mallocPointer(1);
			PointerBuffer lengths = stack.mallocPointer(1);

			// TODO delete memory?

			try {
				source = CLUtils.ioResourceToByteBuffer("Particles.cl", 4096);
			} catch (IOException e) {
				throw new OpenCLException(e.getMessage());
			}

			strings.put(0, source);
			lengths.put(0, source.remaining());

			clProgram = clCreateProgramWithSource(clContext, strings, lengths, errcode_ret);
			CLInfo.checkCLError(clBuildProgram(clProgram, clDevice, "", programCB, NULL));
			clBitonicSortLocal = clCreateKernel(clProgram, "bitonicSortLocal", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clBitonicSortLocal1 = clCreateKernel(clProgram, "bitonicSortLocal1", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clBitonicMergeGlobal = clCreateKernel(clProgram, "bitonicMergeGlobal", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clBitonicMergeLocal = clCreateKernel(clProgram, "bitonicMergeLocal", errcode_ret);
			CLInfo.checkCLError(errcode_ret);

			clCalcHash = clCreateKernel(clProgram, "calcHash", errcode_ret);
			CLInfo.checkCLError(errcode_ret);
			clFindCellBoundsAndReorder = clCreateKernel(clProgram, "findCellBoundsAndReorder", errcode_ret);
			CLInfo.checkCLError(errcode_ret);

			PointerBuffer pp = stack.mallocPointer(1);
			clGetDeviceInfo(clDevice, CL_DEVICE_MAX_WORK_GROUP_SIZE, pp, null);
			max_work_group_size = (int)pp.get(0);

			logger.info("CL_DEVICE_MAX_WORK_GROUP_SIZE = " + max_work_group_size);
		}

	}
}
