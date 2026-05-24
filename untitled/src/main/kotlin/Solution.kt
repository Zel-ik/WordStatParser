package org.paring

class Solution {

    fun twoSum(nums: IntArray, target: Int): IntArray {
        val isTargetBiggerThenLastInArray: Boolean;
        nums.sort()
        var fisrtVar = nums[nums.size -1] - target
    }

    fun checkMiddle(nums: IntArray, target: Int): Int {
        val middle = nums.size/2
        if (nums[nums.size/2] == target)
            return nums[middle]
        else if (nums[nums.size/2] > target)
            return checkMiddle(nums.copyOfRange(nums.size/2, nums.size-1), target)
        else
            return checkMiddle(nums.copyOfRange(0, nums.size/2), target)
    }
}