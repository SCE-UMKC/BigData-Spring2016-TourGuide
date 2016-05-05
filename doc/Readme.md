Autonomous Tour Guide

CS5542 Spring 2016

Project Team 6

Sindhu Reddy Golconda
Simon Moeller
Prudhvi Raj Mudunuri
Sudhakar Reddy Peddinti


Demo video:

https://youtu.be/mfwn3Rjcl3Q




Use case: allow a person, possibly with impeded movement capabilities, a more quickly find the desired object in a large space, such as a mall

The concept is that an Android powered robot would be released into the new area as the user enters. The robot would navigate throughout the new area, sending a stream of images back to a backend processing server. The backend processing server would normalize the images, stitch them together, and then run object and character recognition against them. A database would be saved of all identified objects. The user could then use a smartwatch also connected to the backend processing server to request the desired object, which could be a store, a location like bathroom, or a desired item like "dress shirt". The backend will then identify the best match from all identified objects and text, and provide basic relative directions to the user, similar to "the dress shirt store XXX is ahead on your left 30 meters".