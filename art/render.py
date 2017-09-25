#! /bin/env python

import xml.etree.ElementTree as ET
import subprocess

ns = {
	"dc": "http://purl.org/dc/elements/1.1/",
	"cc": "http://creativecommons.org/ns#",
	"rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
	"svg": "http://www.w3.org/2000/svg",
	"sodipodi": "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd",
	"inkscape": "http://www.inkscape.org/namespaces/inkscape",
}
for name, url in ns.items():
	ET.register_namespace(name, url)

resolutions = {
	'mdpi': 1,
	'hdpi': 1.5,
	'xhdpi': 2,
	'xxhdpi': 3,
	'xxxhdpi': 4,
}

images = {
	# 'ic_launcher.svg':  ('ic_launcher', 48),
	# 'main_logo.svg':  ('main_logo', 200),
	# 'play_video.svg':  ('play_video', 128),
	# 'play_gif.svg':  ('play_gif', 128),
	# 'conversations_mono.svg':  ('ic_notification', 24),
	# 'ic_received_indicator.svg':  ('ic_received_indicator', 12),
	# 'ic_send_text_offline.svg':  ('ic_send_text_offline', 36),
	# 'ic_send_text_offline_white.svg':  ('ic_send_text_offline_white', 36),
	# 'ic_send_text_online.svg':  ('ic_send_text_online', 36),
	# 'ic_send_text_away.svg':  ('ic_send_text_away', 36),
	# 'ic_send_text_dnd.svg':  ('ic_send_text_dnd', 36),
	# 'ic_send_photo_online.svg':  ('ic_send_photo_online', 36),
	# 'ic_send_photo_offline.svg':  ('ic_send_photo_offline', 36),
	# 'ic_send_photo_offline_white.svg':  ('ic_send_photo_offline_white', 36),
	# 'ic_send_photo_away.svg':  ('ic_send_photo_away', 36),
	# 'ic_send_photo_dnd.svg':  ('ic_send_photo_dnd', 36),
	# 'ic_send_location_online.svg':  ('ic_send_location_online', 36),
	# 'ic_send_location_offline.svg':  ('ic_send_location_offline', 36),
	# 'ic_send_location_offline_white.svg':  ('ic_send_location_offline_white', 36),
	# 'ic_send_location_away.svg':  ('ic_send_location_away', 36),
	# 'ic_send_location_dnd.svg':  ('ic_send_location_dnd', 36),
	# 'ic_send_voice_online.svg':  ('ic_send_voice_online', 36),
	# 'ic_send_voice_offline.svg':  ('ic_send_voice_offline', 36),
	# 'ic_send_voice_offline_white.svg':  ('ic_send_voice_offline_white', 36),
	# 'ic_send_voice_away.svg':  ('ic_send_voice_away', 36),
	# 'ic_send_voice_dnd.svg':  ('ic_send_voice_dnd', 36),
	# 'ic_send_cancel_online.svg':  ('ic_send_cancel_online', 36),
	# 'ic_send_cancel_offline.svg':  ('ic_send_cancel_offline', 36),
	# 'ic_send_cancel_offline_white.svg':  ('ic_send_cancel_offline_white', 36),
	# 'ic_send_cancel_away.svg':  ('ic_send_cancel_away', 36),
	# 'ic_send_cancel_dnd.svg':  ('ic_send_cancel_dnd', 36),
	# 'ic_send_picture_online.svg':  ('ic_send_picture_online', 36),
	# 'ic_send_picture_offline.svg':  ('ic_send_picture_offline', 36),
	# 'ic_send_picture_offline_white.svg':  ('ic_send_picture_offline_white', 36),
	# 'ic_send_picture_away.svg':  ('ic_send_picture_away', 36),
	# 'ic_send_picture_dnd.svg':  ('ic_send_picture_dnd', 36),
	# 'ic_notifications_none_white80.svg':  ('ic_notifications_none_white80', 24),
	# 'ic_notifications_off_white80.svg':  ('ic_notifications_off_white80', 24),
	# 'ic_notifications_paused_white80.svg':  ('ic_notifications_paused_white80', 24),
	# 'ic_notifications_white80.svg':  ('ic_notifications_white80', 24),
	# 'ic_verified_fingerprint.svg':  ('ic_verified_fingerprint', 36),
	# 'md_switch_thumb_disable.svg':  ('switch_thumb_disable', 48),
	# 'md_switch_thumb_off_normal.svg':  ('switch_thumb_off_normal', 48),
	# 'md_switch_thumb_off_pressed.svg':  ('switch_thumb_off_pressed', 48),
	# 'md_switch_thumb_on_normal.svg':  ('switch_thumb_on_normal', 48),
	# 'md_switch_thumb_on_pressed.svg':  ('switch_thumb_on_pressed', 48),
	# 'message_bubble_received.svg':  ('message_bubble_received.9', 0),
	# 'message_bubble_received_grey.svg':  ('message_bubble_received_grey.9', 0),
	# 'message_bubble_received_dark.svg':  ('message_bubble_received_dark.9', 0),
	'message_bubble_received_blue.svg':  ('message_bubble_received_blue.9', 0),
	'message_bubble_received_blue_dark.svg':  ('message_bubble_received_blue_dark.9', 0),
	# 'message_bubble_received_warning.svg': ('message_bubble_received_warning.9', 0),
	# 'message_bubble_received_white.svg': ('message_bubble_received_white.9', 0),
	# 'message_bubble_sent.svg': ('message_bubble_sent.9', 0),
	# 'message_bubble_sent_grey.svg': ('message_bubble_sent_grey.9', 0),
	# 'date_bubble_white.svg': ('date_bubble_white.9', 0),
	# 'date_bubble_grey.svg': ('date_bubble_grey.9', 0),
}

# Executable paths for Mac OSX
# "/Applications/Inkscape.app/Contents/Resources/bin/inkscape"

inkscape = "inkscape"
imagemagick = "convert"

def flatten(arr):
	for e in arr:
		if isinstance(e, (list, tuple)):
			for f in flatten(e):
				yield f
		else:
			yield e



def execute_cmd(*args):
	args = list(map(str, flatten(args)))
	print args
	subprocess.call(args, shell=True)


for (source_filename, settings) in images.items():
	svg = ET.parse(source_filename)
	svg = svg.getroot()

	base_width = int(svg.attrib["width"])
	base_height = int(svg.attrib["height"])
	guides = svg.findall(".//sodipodi:guide", ns)

	for resolution, factor in resolutions.items():
		output_filename, base_size = settings

		if base_size > 0:
			width = factor * base_size
			height = factor * base_size
		else:
			width = factor * base_width
			height = factor * base_height

		path = "../src/main/res/drawable-{resolution}/{output_filename}.png".format(resolution=resolution, output_filename=output_filename)
		execute_cmd(inkscape, "-f", source_filename, "-z", "-C", "-w", int(width), "-h", int(height), "-e", path)


		top = []
		right = []
		bottom = []
		left = []

		for guide in guides:
			orientation = guide.attrib["orientation"]
			x, y = guide.attrib["position"].split(",")
			x, y = int(x), int(y)

			if orientation == "1,0" and y == base_height:
				top.append(x * factor)

			if orientation == "0,1" and x == base_width:
				right.append((base_height - y) * factor)

			if orientation == "1,0" and y == 0:
				bottom.append(x * factor)

			if orientation == "0,1" and x == 0:
				left.append((base_height - y) * factor)

		if len(top) != 2: continue
		if len(right) != 2: continue
		if len(bottom) != 2: continue
		if len(left) != 2: continue


		execute_cmd(imagemagick, "-background", "none", "PNG32:" + path, "-gravity", "center", "-extent", "{}x{}".format(int(width)+2, int(height)+2), "PNG32:" + path)

		draw_format = "rectangle %d,%d %d,%d"
		top_line = ("-draw", draw_format % (min(top) + 1, 0, max(top), 0))
		right_line = ("-draw", draw_format % (width + 1, min(right) + 1, width + 1, max(right)))
		bottom_line = ("-draw", draw_format % (min(bottom) + 1, height + 1, max(bottom), height + 1))
		left_line = ("-draw", draw_format % (0, min(left) + 1, 0, max(left)))
		draws = [top_line, right_line, bottom_line, left_line]

		execute_cmd(imagemagick, "-background", "none", "PNG32:"+path, "-fill", "black", "-stroke", "none", draws, "PNG32:"+path)


