/**
*  This file is part of FNLP (formerly FudanNLP).
*  
*  FNLP is free software: you can redistribute it and/or modify
*  it under the terms of the GNU Lesser General Public License as published by
*  the Free Software Foundation, either version 3 of the License, or
*  (at your option) any later version.
*  
*  FNLP is distributed in the hope that it will be useful,
*  but WITHOUT ANY WARRANTY; without even the implied warranty of
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*  GNU Lesser General Public License for more details.
*  
*  You should have received a copy of the GNU General Public License
*  along with FudanNLP.  If not, see <http://www.gnu.org/licenses/>.
*  
*  Copyright 2009-2014 www.fnlp.org. All rights reserved. 
*/

package org.fnlp.ml.cluster;

import java.util.ArrayList;
import java.util.Iterator;

import org.fnlp.ml.types.Instance;
import org.fnlp.ml.types.sv.HashSparseVector;

public class Kmeans {
	int k;
	private final double TOL = 0.0;
	public HashSparseVector[] centroids = null;
	private HashSparseVector[] newCentroids = null;
	private ArrayList<Instance>[] assignedClusters = null;
	private ArrayList<Instance>[] newClusters = null;
	private float[] clusterQualities = null;
	
	
	private float[] newQualities = null;
	int maxIterations = 10;

	/**
	 * Creates a new instance of Kmeans
	 *
	 * @param k
	 */
	public Kmeans (int k) {
		this.k = k;
		this.centroids = new HashSparseVector[k];
		this.assignedClusters = new ArrayList[k];
		this.clusterQualities = new float[k];
		this.newCentroids = new HashSparseVector[k];
		this.newClusters = new ArrayList[k];
		this.newQualities = new float[k];
	}

	/**
	 * 计算类中心
	 *
	 * @param insts
	 *
	 * @return
	 */
	private HashSparseVector calculateCentroid (ArrayList<Instance> insts) {
		HashSparseVector centroid = new HashSparseVector();
		
		
		Iterator i = insts.iterator();

		while (i.hasNext()) {
			Instance d = (Instance) i.next();

			centroid.plus((HashSparseVector) d.getData());
		}
		centroid.scaleDivide(insts.size());

		return centroid;
	}

	/**
	 * 类内方差
	 *
	 * @param docs
	 * @param centroid
	 *
	 * @return
	 */
	private float calculateClusterQuality (ArrayList<Instance> docs,
			HashSparseVector centroid) {
		float quality = 0.0f;
		HashSparseVector c = centroid;

		for (int i = 0; i < docs.size(); ++i) {
			Instance doc = docs.get(i);

			quality += c.distanceEuclidean((HashSparseVector) doc.getData());
		}

		return quality;
	}

	/**
	 * 总体方差
	 *
	 * @param docs
	 * @param centroid
	 *
	 * @return
	 */
	private double calculatePartitionQuality (ArrayList<Instance>[] docs,
			HashSparseVector[] centroid) {
		double quality = 0.0;

		for (int i = 0; i < docs.length; ++i) {
			quality += this.calculateClusterQuality(docs[i], centroid[i]);
		}
		return quality;
	}

	/**
	 * 聚类
	 * @param insts
	 */
	public void cluster (ArrayList<Instance> insts) {

		
		System.out.println("Initial centers");
		for(int i=0;i<k;i++){
			assignedClusters[i] = new ArrayList<Instance>();
		}
		for(int i=0;i<insts.size();i++){
			assignedClusters[i%k].add(insts.get(i));
		}
		for(int i=0;i<k;i++){
			centroids[i] = calculateCentroid(assignedClusters[i]);
			clusterQualities[i] = calculateClusterQuality(assignedClusters[i], centroids[i]);
		}
		
		

		for (int numChanged = 0, itr = 0; (numChanged > 0) || (itr == 0); ++itr) {

			numChanged = 0;

			while (true) {

				int numReassigned = doBatchKmeans();

				System.out.println("After an iteration of Batch K-Means, " +
						numReassigned + " documents were moved.");

				double oldQuality = 0.0;
				double newQuality = 0.0;

				for (int b = 0; b < this.centroids.length; ++b) {
					oldQuality += this.clusterQualities[b];
					newQuality += this.newQualities[b];
				}

				double qualityDelta = oldQuality - newQuality;

				System.out.println("Change in quality is: " + qualityDelta);

				if (qualityDelta < this.TOL) {
					System.out.println(
							"Benefit of change is below tolerance... Switching to incremental...\n");

					break;
				}

				if (numReassigned == 0) {
					System.out.println(
							"Batch K-Means has made no changes! Switching to incremental...\n");

					break;
				}

				// We like the new results. Let's make them authoritative
				for (int kk = 0; kk < this.assignedClusters.length; ++kk) {
					this.assignedClusters[kk] = this.newClusters[kk];
					this.centroids[kk] = this.newCentroids[kk];
					this.clusterQualities[kk] = this.newQualities[kk];
				}

				numChanged = numReassigned;    // Record the fact we made a change!
			}

			double qual = 0.0;

			for (int i = 0; i < this.clusterQualities.length; ++i) {
				qual += this.clusterQualities[i];
			}

			System.out.println("Quality of partition generated by Batch K-Means: " +
					qual);
		}

		System.out.println("Batch K-Means Complete!\n");
		
	}

	/**
	 * Performs one iteration of batch k-means. Returns the number of documents that
	 * were moved during this iteration. This method also updates the global variables
	 * newClusters[] and newCentroids[] to the values. It's up to the caller to copy these
	 * over the current assignedClusters[] and centroids[] arrays if desired.  Initial centroids of
	 * each initial cluster must be built in the constructor.
	 *
	 * @return
	 */
	private int doBatchKmeans () {

		System.out.println("\nBegining a new iteration of K-Means...");

		int numReassigned = 0;

		/* Clear records for incremental k-means */

		for (int i = 0; i < this.centroids.length; ++i) {
			this.newClusters[i] = new ArrayList<Instance>();
			this.newCentroids[i] = new HashSparseVector();
			this.newQualities[i] = 0.0f;
		}

		for (int clusterNum = 0; clusterNum < this.centroids.length; ++clusterNum) {    // iterate over clusters
			for (int docNum = 0; docNum < this.assignedClusters[clusterNum].size();	++docNum) {    // iterate over docs

				/*
				 *  Store the document the loops have selected in the 'doc' variable.
				 * Store is vector in the 'docVec' variable for easy access.
				 */
				Instance doc = this.assignedClusters[clusterNum].get(docNum);
				HashSparseVector docVec = (HashSparseVector) doc.getData();

				int bestClusterNum = clusterNum;    // Assume we are already in the best cluster.
				double distanceToCurrentCentroid =
					this.centroids[clusterNum].distanceEuclidean(docVec);
				double squareDistanceOfBestCluster = distanceToCurrentCentroid;

				for (int i = 0; i < this.centroids.length; ++i) {

					double distance = 0.0;

					// see which centroid is closest to docVec
					if (clusterNum == i) {    // We know the distance in its' current cluster.
						distance = distanceToCurrentCentroid;
					} else {
						distance = this.centroids[i].distanceEuclidean(docVec);

					}

					if (distance < squareDistanceOfBestCluster) {
						squareDistanceOfBestCluster = distance;
						bestClusterNum = i;
					}
				}

				if (bestClusterNum != clusterNum) {    // we moved a document!
					++numReassigned;
				}

				this.newClusters[bestClusterNum].add(doc);
				this.newCentroids[bestClusterNum].plus(docVec);
			}
		}

		// Calculate the centroids of the clusters
		for (int i = 0; i < newClusters.length; ++i) {
			this.newCentroids[i].scaleDivide(this.newClusters[i].size());

			this.newQualities[i] = this.calculateClusterQuality(this.newClusters[i],
					this.newCentroids[i]);

			System.out.println("new cluster " + i + " Viarances: " +
					this.newQualities[i] + " Num: "+ newClusters[i].size());
		}

		return (numReassigned);
	}

}