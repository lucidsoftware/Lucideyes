# Lucideyes
*Easily compare images for visual equality without pixel perfection.*

## Visual validations
Complement automated testing of elements, attributes, interactions and flow by verifying visual experience.

## Getting Started
After importing the API into your Java project, make a new ImageCompare object by providing two images.

    ImageCompare imagecompare =
    ImageCompare.apply(masterImage, snapshotImage)
Access results from the `ImageCompare` object.

    imagecompare.isMatch()
    imagecompare.isSameSize()
    imagecompare.getCircledDiff()
    imagecompare.getPixelDiff()

## Adjusting controls
Lucideyes verifies images by creating uniform blocks of averaged pixels.  It then checks if color distance between corresponding blocks exceeds a defined threshold.

Adjusting **block size** and **max color distance** defines strictness of comparison.

Create a new MatchLevel object to customize control.  For example, the match comparison will average 11 pixel blocks and allow a maximum color distance of 20:

    ImageCompare imageCompare =
    ImageCompare.apply(
		    masterImage,
		    snapshotImage,
		    MatchLevel.apply(11, 20)
	    )

## MatchLevel Presets
Lucideyes comes with the following preset options:

| MatchLevel 	| BlockSize 	| MaxColorDistance 	| ImageDifference 	|
|------------	|-----------	|------------------	|---------------------	|
| EXACT 	| 1 	| 1 	| None 	|
| STRICT 	| 5 	| 14 	| Not Noticeable 	|
| TOLERANT 	| 10 	| 25 	| Slightly Noticeable 	|

For example:

    ImageCompare imageCompare =
        ImageCompare.apply(
    		    masterImage,
    		    snapshotImage,
    		    MatchLevel.TOLERANT
    	    	)
## Masks
Define masks to disregard or exclusively consider certain areas within the image.

For example, this provides rectangle coordinates (x, y, width, height) to focus on and exclude regions when comparing these images:

    Set<Region> mask = new HashSet<>();
    mask.add(Region.apply(0,0,200,200,RegionAction.FOCUS);
    mask.add(Region.apply(10,10,15,15,RegionAction.EXCLUDE);
    ImageCompare imageCompare =
	    ImageCompare.apply(
		    masterImage,
		    snapshotImage,
		    MatchLevel.TOLERANT,
		    mask
	    )



## Lucideyes
Lucideyes came from the need to automate visual testing in Lucidchart and Lucidpress.