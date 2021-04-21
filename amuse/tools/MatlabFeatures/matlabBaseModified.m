
%
% matlab_base.m
%
% loads one input wav files and extracts all Matlab features and saves results as text files
% one text file per feature is created
%
% The files have been decoded, subsampled by a factor of 2 and
% converted to mono. 
%

function matlab_base(input_file,output_dir)

% Add the paths to AMUSE Matlab libraries
addpath('/home/speckman/Dokumente/AMUSE/amuse/tools/MatlabFeatures');

% define window length of analysis window in number of samples
wlength = 512;

% define window offset in number of samples
woffset = wlength;

% for the cutting of the path to file and its extension
p=strfind(input_file,filesep);
k=strfind(input_file,'.');

% create Amuse ARFF feature file
output_file_prefix = [output_dir,filesep,input_file(p(end)+1:k(end)-1),'_'];

% loads wav input file, called input.wav
[x,fs] = amuse_audio_read(input_file);

% determine number of windows
s = size(x);
nwin = floor(1 + (s(1)-wlength)/woffset);

	% compute CMRARE cepstral modulation features with polynomial order 3
	res = f_45_CMRARE_start(input_file);
	
	% save the results
	arff_saver_with_windows_number([output_file_prefix,'45.arff'],'CMRARE cepstral modulation features with polynomial order 3',res,110250,110250);

	% compute CMRARE cepstral modulation features with polynomial order 5
	res = f_46_CMRARE_start(input_file);
	
	% save the results
	arff_saver_with_windows_number([output_file_prefix,'46.arff'],'CMRARE cepstral modulation features with polynomial order 5',res,110250,110250);

	% compute CMRARE cepstral modulation features with polynomial order 10
	res = f_47_CMRARE_start(input_file);
	
	% save the results
	arff_saver_with_windows_number([output_file_prefix,'47.arff'],'CMRARE cepstral modulation features with polynomial order 10',res,110250,110250);

% compute duration of input file in seconds
res = f_400_duration(input_file);

% save the results
arff_saver([output_file_prefix,'400.arff'],'Duration',res,-1);

% compute the fundamental frequency
ff = f_200_fundamental_freq(x,fs,wlength,woffset);

% compute first two tristimulus values and normalized energy of harmonic components
res = f_7_10_harmonic_analysis(x,fs,wlength,woffset,ff);

% save the results
arff_saver_with_windows_number([output_file_prefix,'200.arff'],'Fundamental frequency',ff,wlength,woffset);

% save the results
arff_saver_with_windows_number([output_file_prefix,'7.arff'],'Normalized energy of harmonic components',res(3,:),wlength,woffset);

% save the results
arff_saver_with_windows_number([output_file_prefix,'10.arff'],'Tristimulus',[res(1,:)' res(2,:)']',wlength,woffset);

exit
